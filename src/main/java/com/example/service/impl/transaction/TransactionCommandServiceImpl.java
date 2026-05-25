package com.example.service.impl.transaction;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.RedisService;
import com.example.domain.requests.transactions.CreateTransactionRequest;
import com.example.domain.requests.transactions.UpdateTransactionRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.transaction.TransactionResponse;
import com.example.domain.response.transaction.TransactionResponseDeleteAt;
import com.example.entity.OrderItem;
import com.example.entity.ShippingAddress;
import com.example.entity.order.Order;
import com.example.entity.transaction.Transaction;
import com.example.enums.PaymentStatus;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.merchant.MerchantQueryRepository;
import com.example.repository.order.OrderQueryRepository;
import com.example.repository.orderitem.OrderItemRepository;
import com.example.repository.shippingaddress.ShippingAddressQueryRepository;
import com.example.repository.transaction.TransactionCommandRepository;
import com.example.repository.transaction.TransactionQueryRepository;
import com.example.service.transaction.TransactionCommandService;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionCommandServiceImpl implements TransactionCommandService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionCommandServiceImpl.class);

    MerchantQueryRepository merchantQueryRepository;
    TransactionQueryRepository transactionQueryRepository;
    OrderQueryRepository orderQueryRepository;
    OrderItemRepository orderItemRepository;
    ShippingAddressQueryRepository shippingAddressQueryRepository;
    TransactionCommandRepository transactionCommandRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public TransactionCommandServiceImpl(MerchantQueryRepository merchantQueryRepository,
            TransactionQueryRepository transactionQueryRepository,
            OrderQueryRepository orderQueryRepository,
            OrderItemRepository orderItemRepository,
            ShippingAddressQueryRepository shippingAddressQueryRepository,
            TransactionCommandRepository transactionCommandRepository,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.merchantQueryRepository = merchantQueryRepository;
        this.transactionQueryRepository = transactionQueryRepository;
        this.orderQueryRepository = orderQueryRepository;
        this.orderItemRepository = orderItemRepository;
        this.shippingAddressQueryRepository = shippingAddressQueryRepository;
        this.transactionCommandRepository = transactionCommandRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("transaction-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("transaction-command-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    private Uni<Void> invalidateCache(Long txId, Integer orderId) {
        Uni<Void> deleteTx = txId != null ? redisService.deleteReactive("transaction:id:" + txId).replaceWithVoid()
                : Uni.createFrom().voidItem();
        Uni<Void> deleteOrderTx = orderId != null
                ? redisService.deleteReactive("transaction:order:" + orderId).replaceWithVoid()
                : Uni.createFrom().voidItem();
        return Uni.combine().all().unis(deleteTx, deleteOrderTx).discardItems()
                .chain(v -> redisService.deleteReactive("transaction:all:*").replaceWithVoid())
                .chain(v -> redisService.deleteReactive("transaction:active:*").replaceWithVoid())
                .chain(v -> redisService.deleteReactive("transaction:trashed:*").replaceWithVoid())
                .chain(v -> redisService.deleteReactive("transaction:merchant:*").replaceWithVoid())
                .onFailure().recoverWithItem((Void) null);
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<TransactionResponse>> create(CreateTransactionRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createTransaction")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "create_transaction")
                .setAttribute("order.id", req.getOrderID() != null ? req.getOrderID().toString() : "null")
                .setAttribute("merchant.id", req.getMerchantID() != null ? req.getMerchantID().toString() : "null")
                .startSpan();

        logger.info("💳 Creating new transaction | orderId={}, merchantId={}", req.getOrderID(), req.getMerchantID());

        return merchantQueryRepository.findMerchantById(req.getMerchantID().longValue())
                .chain(merchant -> {
                    if (merchant == null) {
                        logger.error("❌ Merchant not found | merchantId={}", req.getMerchantID());
                        throw new ResourceNotFoundException("Merchant not found");
                    }

                    return orderQueryRepository.findOrderById(req.getOrderID().longValue());
                })
                .chain(orderOpt -> {
                    if (orderOpt.isEmpty()) {
                        logger.error("❌ Order not found | orderId={}", req.getOrderID());
                        throw new ResourceNotFoundException("Order not found");
                    }
                    Order order = orderOpt.get();

                    return Uni.combine().all().unis(
                            orderItemRepository.findOrderItemByOrder(order.id),
                            shippingAddressQueryRepository.findByOrderId(order.id.intValue())).asTuple()
                            .chain(tuple -> {
                                List<OrderItem> orderItems = tuple.getItem1();
                                Optional<ShippingAddress> shippingOpt = tuple.getItem2();

                                if (orderItems.isEmpty()) {
                                    logger.error("❌ No order items found | orderId={}", req.getOrderID());
                                    throw new IllegalArgumentException("No order items found");
                                }

                                if (shippingOpt.isEmpty()) {
                                    logger.error("❌ Shipping address not found | orderId={}", req.getOrderID());
                                    throw new ResourceNotFoundException("Shipping address not found");
                                }

                                ShippingAddress shipping = shippingOpt.get();

                                int totalAmount = 0;
                                for (OrderItem item : orderItems) {
                                    if (item.getQuantity() <= 0) {
                                        throw new IllegalArgumentException("Invalid order item quantity");
                                    }
                                    totalAmount += item.getPrice() * item.getQuantity();
                                }
                                totalAmount += shipping.getShippingCost();
                                int ppn = totalAmount * 11 / 100;
                                int totalAmountWithTax = totalAmount + ppn;

                                String paymentStatus = req.getAmount() >= totalAmountWithTax ? "success" : "failed";
                                if (paymentStatus.equals("failed")) {
                                    logger.error("❌ Insufficient payment amount | amount={}, required={}",
                                            req.getAmount(), totalAmountWithTax);
                                    throw new IllegalArgumentException("Insufficient payment amount");
                                }

                                req.setAmount(totalAmountWithTax);
                                req.setPaymentStatus(paymentStatus);

                                Transaction transaction = new Transaction();
                                transaction.setOrderId(req.getOrderID());
                                transaction.setMerchantId(req.getMerchantID());
                                transaction.setPaymentMethod(req.getPaymentMethod());
                                transaction.setAmount(req.getAmount());
                                transaction.setStatus(PaymentStatus.fromValue(req.getPaymentStatus()));

                                return transactionCommandRepository.persist(transaction);
                            });
                })
                .chain(saved -> {
                    TransactionResponse response = TransactionResponse.from(saved);
                    span.setAttribute("transaction.id", saved.id);

                    return invalidateCache(saved.id, saved.getOrderId())
                            .map(v -> {
                                logger.info("✅ Transaction created successfully | transactionId={}", saved.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "create_transaction",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Transaction created successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to create transaction", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_transaction",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_transaction"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<TransactionResponse>> update(UpdateTransactionRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateTransaction")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "update_transaction")
                .setAttribute("transaction.id",
                        req.getTransactionID() != null ? req.getTransactionID().toString() : "null")
                .startSpan();

        logger.info("✏️ Updating transaction | transactionId={}", req.getTransactionID());

        return transactionQueryRepository.findTransactionById(req.getTransactionID().longValue())
                .chain(existingTx -> {
                    if (existingTx.isEmpty()) {
                        logger.error("❌ Transaction not found | transactionId={}", req.getTransactionID());
                        throw new ResourceNotFoundException("Transaction not found");
                    }
                    Transaction tx = existingTx.get();

                    if (PaymentStatus.SUCCESS.equals(tx.getStatus()) ||
                            PaymentStatus.REFUNDED.equals(tx.getStatus())) {
                        logger.error("❌ Transaction cannot be modified | transactionId={}", req.getTransactionID());
                        throw new IllegalArgumentException("Transaction cannot be modified");
                    }

                    return merchantQueryRepository.findMerchantById(req.getMerchantID().longValue())
                            .chain(merchant -> {
                                if (merchant == null) {
                                    logger.error("❌ Merchant not found | merchantId={}", req.getMerchantID());
                                    throw new ResourceNotFoundException("Merchant not found");
                                }

                                return orderQueryRepository.findOrderById(req.getOrderID().longValue());
                            })
                            .chain(orderOpt -> {
                                if (orderOpt.isEmpty()) {
                                    logger.error("❌ Order not found | orderId={}", req.getOrderID());
                                    throw new ResourceNotFoundException("Order not found");
                                }
                                Order order = orderOpt.get();

                                return Uni.combine().all().unis(
                                        orderItemRepository.findOrderItemByOrder(order.id),
                                        shippingAddressQueryRepository.findByOrderId(order.id.intValue())).asTuple()
                                        .chain(tuple -> {
                                            List<OrderItem> orderItems = tuple.getItem1();
                                            Optional<ShippingAddress> shippingOpt = tuple.getItem2();

                                            if (orderItems.isEmpty()) {
                                                logger.error("❌ No order items found | orderId={}", req.getOrderID());
                                                throw new IllegalArgumentException("No order items found");
                                            }

                                            if (shippingOpt.isEmpty()) {
                                                logger.error("❌ Shipping address not found | orderId={}",
                                                        req.getOrderID());
                                                throw new ResourceNotFoundException("Shipping address not found");
                                            }

                                            ShippingAddress shipping = shippingOpt.get();

                                            int totalAmount = 0;
                                            for (OrderItem item : orderItems) {
                                                if (item.getQuantity() <= 0) {
                                                    throw new IllegalArgumentException("Invalid order item quantity");
                                                }
                                                totalAmount += item.getPrice() * item.getQuantity();
                                            }
                                            totalAmount += shipping.getShippingCost();
                                            int ppn = totalAmount * 11 / 100;
                                            int totalAmountWithTax = totalAmount + ppn;

                                            String paymentStatus = req.getAmount() >= totalAmountWithTax ? "success"
                                                    : "failed";
                                            if (paymentStatus.equals("failed")) {
                                                logger.error("❌ Insufficient payment amount | amount={}, required={}",
                                                        req.getAmount(), totalAmountWithTax);
                                                throw new IllegalArgumentException("Insufficient payment amount");
                                            }

                                            req.setAmount(totalAmountWithTax);
                                            req.setPaymentStatus(paymentStatus);

                                            tx.setOrderId(req.getOrderID());
                                            tx.setMerchantId(req.getMerchantID());
                                            tx.setPaymentMethod(req.getPaymentMethod());
                                            tx.setAmount(req.getAmount());
                                            tx.setStatus(PaymentStatus.fromValue(req.getPaymentStatus()));

                                            return transactionCommandRepository.persist(tx);
                                        });
                            });
                })
                .chain(updated -> {
                    TransactionResponse response = TransactionResponse.from(updated);

                    return invalidateCache(updated.id, updated.getOrderId())
                            .map(v -> {
                                logger.info("✅ Transaction updated successfully | transactionId={}", updated.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "update_transaction",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Transaction updated successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to update transaction", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_transaction",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_transaction"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<TransactionResponseDeleteAt>> trash(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashTransaction")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "trash_transaction")
                .setAttribute("transaction.id", id.toString())
                .startSpan();

        logger.info("🗑️ Trashing transaction id={}", id);

        return transactionCommandRepository.trashed(id.longValue())
                .chain(transaction -> {
                    if (transaction == null) {
                        throw new ResourceNotFoundException("Transaction not found or already trashed");
                    }
                    TransactionResponseDeleteAt response = TransactionResponseDeleteAt.from(transaction);

                    return invalidateCache(id.longValue(), transaction.getOrderId())
                            .map(v -> {
                                logger.info("Successfully trashed transaction with ID: {}", id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_transaction",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("🗑️ Transaction trashed successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to trash transaction id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_transaction",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_transaction"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<TransactionResponseDeleteAt>> restore(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreTransaction")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "restore_transaction")
                .setAttribute("transaction.id", id.toString())
                .startSpan();

        logger.info("♻️ Restoring transaction id={}", id);

        return transactionCommandRepository.restore(id.longValue())
                .chain(transaction -> {
                    if (transaction == null) {
                        throw new ResourceNotFoundException("Transaction not found or not trashed");
                    }
                    TransactionResponseDeleteAt response = TransactionResponseDeleteAt.from(transaction);

                    return invalidateCache(id.longValue(), transaction.getOrderId())
                            .map(v -> {
                                logger.info("Successfully restored transaction with ID: {}", id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_transaction",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("♻️ Transaction restored successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore transaction id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_transaction",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_transaction"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> delete(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteTransactionPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "delete_transaction_permanent")
                .setAttribute("transaction.id", id.toString())
                .startSpan();

        logger.warn("🧨 Permanently deleting transaction id={}", id);

        return transactionCommandRepository.deletePermanent(id.longValue())
                .chain(deleted -> {
                    return invalidateCache(id.longValue(), null)
                            .map(v -> {
                                logger.info("Successfully permanently deleted transaction with ID: {}", id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "delete_transaction_permanent",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("🧨 Transaction permanently deleted!", deleted);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to permanently delete transaction id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_transaction_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_transaction_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllTransactions")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "restore_all_transactions")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed transactions");

        return transactionCommandRepository.restoreAllDeleted()
                .chain(restored -> {
                    return invalidateCache(null, null)
                            .map(v -> {
                                logger.info("Successfully restored all trashed transactions");
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_all_transactions",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("🔄 All transactions restored successfully!", restored);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore all transactions", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_transactions",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_transactions"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllTransactionsPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "delete_all_transactions_permanent")
                .startSpan();

        logger.warn("💣 Permanently deleting ALL trashed transactions");

        return transactionCommandRepository.deleteAllDeleted()
                .chain(deleted -> {
                    return invalidateCache(null, null)
                            .map(v -> {
                                logger.info("Successfully permanently deleted all trashed transactions");
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "delete_all_transactions_permanent",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("💣 All transactions permanently deleted!", deleted);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to delete all transactions", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_transactions_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_transactions_permanent"));
                });
    }
}
