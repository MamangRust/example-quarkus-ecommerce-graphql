package com.example.service.impl.transaction;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.config.RedisService;
import com.example.domain.requests.transactions.FindAllTransactionByMerchantRequest;
import com.example.domain.requests.transactions.FindAllTransactionRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.api.PagedResult;
import com.example.domain.response.api.PaginationMeta;
import com.example.domain.response.transaction.TransactionResponse;
import com.example.domain.response.transaction.TransactionResponseDeleteAt;
import com.example.entity.transaction.Transaction;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.transaction.TransactionQueryRepository;
import com.example.service.transaction.TransactionQueryService;

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
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionQueryServiceImpl implements TransactionQueryService {
        private static final Logger logger = LoggerFactory.getLogger(TransactionQueryServiceImpl.class);

        TransactionQueryRepository transactionQueryRepository;
        OpenTelemetry openTelemetry;
        RedisService redisService;
        ObjectMapper objectMapper;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        private static final long LIST_CACHE_TTL_SECONDS = 300;

        @Inject
        public TransactionQueryServiceImpl(TransactionQueryRepository transactionQueryRepository,
                        OpenTelemetry openTelemetry,
                        RedisService redisService,
                        ObjectMapper objectMapper) {
                this.transactionQueryRepository = transactionQueryRepository;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.objectMapper = objectMapper;
                this.tracer = openTelemetry.getTracer("transaction-query-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("transaction-query-service");

                this.requestsTotal = meter.counterBuilder("requests_total")
                                .setDescription("Total number of requests")
                                .build();
                this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                                .setDescription("Request duration in seconds")
                                .setUnit("s")
                                .build();
        }

        private String toJson(Object obj) {
                try {
                        return objectMapper.writeValueAsString(obj);
                } catch (JsonProcessingException e) {
                        logger.error("Error serializing object to JSON", e);
                        throw new RuntimeException("Failed to serialize object", e);
                }
        }

        private <T> T fromJson(String json, Class<T> clazz) {
                try {
                        return objectMapper.readValue(json, clazz);
                } catch (JsonProcessingException e) {
                        logger.error("Error deserializing JSON to object", e);
                        throw new RuntimeException("Failed to deserialize JSON", e);
                }
        }

        private <T> T fromJson(String json, TypeReference<T> typeReference) {
                try {
                        return objectMapper.readValue(json, typeReference);
                } catch (JsonProcessingException e) {
                        logger.error("Error deserializing JSON to object with TypeReference", e);
                        throw new RuntimeException("Failed to deserialize JSON", e);
                }
        }

        @Override
        public Uni<ApiResponsePagination<List<TransactionResponse>>> findAllTransactions(
                        FindAllTransactionRequest req) {
                String cacheKey = String.format("transaction:all:%d:%d:%s", req.getPage(), req.getPageSize(),
                                req.getSearch() != null ? req.getSearch() : "");

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<TransactionResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<TransactionResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findAllTransactions")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "transaction-service")
                                                        .setAttribute("operation", "find_all_transactions")
                                                        .startSpan();

                                        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                                        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                                        String search = (req.getSearch() != null && !req.getSearch().isEmpty())
                                                        ? req.getSearch()
                                                        : "";

                                        return transactionQueryRepository.findTransactions(search, page, size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("transaction.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("transaction.page", req.getPage());
                                                                span.setAttribute("transaction.size",
                                                                                req.getPageSize());

                                                                ApiResponsePagination<List<TransactionResponse>> response = buildPaginatedResponse(
                                                                                pagedResult, req.getPage(),
                                                                                req.getPageSize(),
                                                                                "Transactions retrieved successfully",
                                                                                TransactionResponse::from);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                LIST_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_all_transactions",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding all transactions", e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_all_transactions",
                                                                                AttributeKey.stringKey("status"),
                                                                                "failed",
                                                                                AttributeKey.stringKey("error_type"),
                                                                                e.getClass().getSimpleName()));
                                                        })
                                                        .eventually(() -> {
                                                                span.end();
                                                                double duration = (System.currentTimeMillis()
                                                                                - startTime) / 1000.0;
                                                                requestDurationSeconds.record(duration, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_all_transactions"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponsePagination<List<TransactionResponseDeleteAt>>> findByActive(
                        FindAllTransactionRequest req) {
                String cacheKey = String.format("transaction:active:%d:%d:%s", req.getPage(), req.getPageSize(),
                                req.getSearch() != null ? req.getSearch() : "");

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<TransactionResponseDeleteAt>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<TransactionResponseDeleteAt>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findActiveTransactions")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "transaction-service")
                                                        .setAttribute("operation", "find_active_transactions")
                                                        .startSpan();

                                        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                                        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                                        String search = (req.getSearch() != null && !req.getSearch().isEmpty())
                                                        ? req.getSearch()
                                                        : "";

                                        return transactionQueryRepository.findActiveTransactions(search, page, size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("transaction.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("transaction.page", req.getPage());
                                                                span.setAttribute("transaction.size",
                                                                                req.getPageSize());

                                                                ApiResponsePagination<List<TransactionResponseDeleteAt>> response = buildPaginatedResponse(
                                                                                pagedResult, req.getPage(),
                                                                                req.getPageSize(),
                                                                                "Active transactions retrieved successfully",
                                                                                TransactionResponseDeleteAt::from);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                LIST_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_active_transactions",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding active transactions", e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_active_transactions",
                                                                                AttributeKey.stringKey("status"),
                                                                                "failed",
                                                                                AttributeKey.stringKey("error_type"),
                                                                                e.getClass().getSimpleName()));
                                                        })
                                                        .eventually(() -> {
                                                                span.end();
                                                                double duration = (System.currentTimeMillis()
                                                                                - startTime) / 1000.0;
                                                                requestDurationSeconds.record(duration, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_active_transactions"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponsePagination<List<TransactionResponseDeleteAt>>> findByTrashed(
                        FindAllTransactionRequest req) {
                String cacheKey = String.format("transaction:trashed:%d:%d:%s", req.getPage(), req.getPageSize(),
                                req.getSearch() != null ? req.getSearch() : "");

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<TransactionResponseDeleteAt>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<TransactionResponseDeleteAt>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findTrashedTransactions")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "transaction-service")
                                                        .setAttribute("operation", "find_trashed_transactions")
                                                        .startSpan();

                                        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                                        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                                        String search = (req.getSearch() != null && !req.getSearch().isEmpty())
                                                        ? req.getSearch()
                                                        : "";

                                        return transactionQueryRepository.findTrashedTransactions(search, page, size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("transaction.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("transaction.page", req.getPage());
                                                                span.setAttribute("transaction.size",
                                                                                req.getPageSize());

                                                                ApiResponsePagination<List<TransactionResponseDeleteAt>> response = buildPaginatedResponse(
                                                                                pagedResult, req.getPage(),
                                                                                req.getPageSize(),
                                                                                "Trashed transactions retrieved successfully",
                                                                                TransactionResponseDeleteAt::from);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                LIST_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_trashed_transactions",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding trashed transactions", e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_trashed_transactions",
                                                                                AttributeKey.stringKey("status"),
                                                                                "failed",
                                                                                AttributeKey.stringKey("error_type"),
                                                                                e.getClass().getSimpleName()));
                                                        })
                                                        .eventually(() -> {
                                                                span.end();
                                                                double duration = (System.currentTimeMillis()
                                                                                - startTime) / 1000.0;
                                                                requestDurationSeconds.record(duration, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_trashed_transactions"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponsePagination<List<TransactionResponse>>> findByMerchant(
                        FindAllTransactionByMerchantRequest req) {
                String cacheKey = String.format("transaction:merchant:%d:%d:%d:%s",
                                req.getMerchantId(), req.getPage(), req.getPageSize(),
                                req.getSearch() != null ? req.getSearch() : "");

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<TransactionResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<TransactionResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findTransactionsByMerchant")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "transaction-service")
                                                        .setAttribute("operation", "find_transactions_by_merchant")
                                                        .setAttribute("merchant.id",
                                                                        req.getMerchantId() != null
                                                                                        ? req.getMerchantId().toString()
                                                                                        : "null")
                                                        .startSpan();

                                        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                                        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                                        String search = (req.getSearch() != null && !req.getSearch().isEmpty())
                                                        ? req.getSearch()
                                                        : "";

                                        return transactionQueryRepository
                                                        .findTransactionsByMerchant(search, req.getMerchantId(), page,
                                                                        size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("transaction.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("transaction.page", req.getPage());
                                                                span.setAttribute("transaction.size",
                                                                                req.getPageSize());

                                                                ApiResponsePagination<List<TransactionResponse>> response = buildPaginatedResponse(
                                                                                pagedResult, req.getPage(),
                                                                                req.getPageSize(),
                                                                                "Merchant transactions retrieved successfully",
                                                                                TransactionResponse::from);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                LIST_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_transactions_by_merchant",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding transactions for merchant: {}",
                                                                                req.getMerchantId(), e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_transactions_by_merchant",
                                                                                AttributeKey.stringKey("status"),
                                                                                "failed",
                                                                                AttributeKey.stringKey("error_type"),
                                                                                e.getClass().getSimpleName()));
                                                        })
                                                        .eventually(() -> {
                                                                span.end();
                                                                double duration = (System.currentTimeMillis()
                                                                                - startTime) / 1000.0;
                                                                requestDurationSeconds.record(duration, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_transactions_by_merchant"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponse<TransactionResponse>> findById(Integer id) {
                String cacheKey = "transaction:id:" + id;

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                TransactionResponse cachedTx = fromJson(cachedJson,
                                                                TransactionResponse.class);
                                                return Uni.createFrom().item(ApiResponse.success(
                                                                "Transaction retrieved successfully", cachedTx));
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findTransactionById")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "transaction-service")
                                                        .setAttribute("operation", "find_transaction_by_id")
                                                        .setAttribute("transaction.id", id.toString())
                                                        .startSpan();

                                        return transactionQueryRepository.findTransactionById(id.longValue())
                                                        .chain(optionalTx -> {
                                                                if (optionalTx.isEmpty()) {
                                                                        logger.warn("Transaction not found with ID: {}",
                                                                                        id);
                                                                        span.setStatus(StatusCode.ERROR,
                                                                                        "Transaction not found");
                                                                        span.setAttribute("transaction.found", false);

                                                                        requestsTotal.add(1, Attributes.of(
                                                                                        AttributeKey.stringKey(
                                                                                                        "operation"),
                                                                                        "find_transaction_by_id",
                                                                                        AttributeKey.stringKey(
                                                                                                        "status"),
                                                                                        "failed",
                                                                                        AttributeKey.stringKey(
                                                                                                        "error_type"),
                                                                                        "not_found"));

                                                                        throw new ResourceNotFoundException(
                                                                                        "Transaction not found with ID: "
                                                                                                        + id);
                                                                }

                                                                Transaction tx = optionalTx.get();
                                                                span.setAttribute("transaction.found", true);
                                                                span.setAttribute("transaction.status",
                                                                                tx.getStatus() != null
                                                                                                ? tx.getStatus().name()
                                                                                                : "null");

                                                                TransactionResponse txResponse = TransactionResponse
                                                                                .from(tx);

                                                                return redisService
                                                                                .setReactive(cacheKey,
                                                                                                toJson(txResponse))
                                                                                .map(v -> {
                                                                                        logger.info("Cached transaction for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_transaction_by_id",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return ApiResponse.success(
                                                                                                        "Transaction retrieved successfully",
                                                                                                        txResponse);
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding transaction by ID: {}", id,
                                                                                e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_transaction_by_id",
                                                                                AttributeKey.stringKey("status"),
                                                                                "failed",
                                                                                AttributeKey.stringKey("error_type"),
                                                                                e.getClass().getSimpleName()));
                                                        })
                                                        .eventually(() -> {
                                                                span.end();
                                                                double duration = (System.currentTimeMillis()
                                                                                - startTime) / 1000.0;
                                                                requestDurationSeconds.record(duration, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_transaction_by_id"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponse<TransactionResponse>> findByOrderId(Integer id) {
                String cacheKey = "transaction:order:" + id;

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                TransactionResponse cachedTx = fromJson(cachedJson,
                                                                TransactionResponse.class);
                                                return Uni.createFrom().item(ApiResponse.success(
                                                                "Transaction retrieved successfully", cachedTx));
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findTransactionByOrderId")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "transaction-service")
                                                        .setAttribute("operation", "find_transaction_by_order_id")
                                                        .setAttribute("order.id", id.toString())
                                                        .startSpan();

                                        return transactionQueryRepository.findByOrderId(id)
                                                        .chain(optionalTx -> {
                                                                if (optionalTx.isEmpty()) {
                                                                        logger.warn("Transaction not found for order ID: {}",
                                                                                        id);
                                                                        span.setStatus(StatusCode.ERROR,
                                                                                        "Transaction not found");
                                                                        span.setAttribute("transaction.found", false);

                                                                        requestsTotal.add(1, Attributes.of(
                                                                                        AttributeKey.stringKey(
                                                                                                        "operation"),
                                                                                        "find_transaction_by_order_id",
                                                                                        AttributeKey.stringKey(
                                                                                                        "status"),
                                                                                        "failed",
                                                                                        AttributeKey.stringKey(
                                                                                                        "error_type"),
                                                                                        "not_found"));

                                                                        throw new ResourceNotFoundException(
                                                                                        "Transaction not found for order ID: "
                                                                                                        + id);
                                                                }

                                                                Transaction tx = optionalTx.get();
                                                                span.setAttribute("transaction.found", true);
                                                                span.setAttribute("transaction.status",
                                                                                tx.getStatus() != null
                                                                                                ? tx.getStatus().name()
                                                                                                : "null");

                                                                TransactionResponse txResponse = TransactionResponse
                                                                                .from(tx);

                                                                return redisService
                                                                                .setReactive(cacheKey,
                                                                                                toJson(txResponse))
                                                                                .map(v -> {
                                                                                        logger.info("Cached transaction for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_transaction_by_order_id",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return ApiResponse.success(
                                                                                                        "Transaction retrieved successfully",
                                                                                                        txResponse);
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding transaction by order ID: {}",
                                                                                id, e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_transaction_by_order_id",
                                                                                AttributeKey.stringKey("status"),
                                                                                "failed",
                                                                                AttributeKey.stringKey("error_type"),
                                                                                e.getClass().getSimpleName()));
                                                        })
                                                        .eventually(() -> {
                                                                span.end();
                                                                double duration = (System.currentTimeMillis()
                                                                                - startTime) / 1000.0;
                                                                requestDurationSeconds.record(duration, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_transaction_by_order_id"));
                                                        });
                                });
        }

        private <T, R> ApiResponsePagination<List<R>> buildPaginatedResponse(
                        PagedResult<T> pagedResult,
                        int pageParam,
                        int sizeParam,
                        String successMessage,
                        Function<T, R> mapper) {

                List<R> data = pagedResult.getData().stream()
                                .map(mapper)
                                .collect(Collectors.toList());

                int totalRecords = pagedResult.getTotalRecords();
                int size = sizeParam > 0 ? sizeParam : 1;
                int totalPages = (int) Math.ceil((double) totalRecords / size);

                PaginationMeta pagination = new PaginationMeta(pageParam, size, totalPages, totalRecords);

                return new ApiResponsePagination<>("success", successMessage, data, pagination);
        }
}
