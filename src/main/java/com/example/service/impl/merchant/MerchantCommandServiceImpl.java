package com.example.service.impl.merchant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.RedisService;
import com.example.domain.requests.merchant.CreateMerchantRequest;
import com.example.domain.requests.merchant.UpdateMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.merchant.MerchantResponse;
import com.example.domain.response.merchant.MerchantResponseDeleteAt;
import com.example.entity.merchant.Merchant;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.UserRepository;
import com.example.repository.merchant.MerchantCommandRepository;
import com.example.repository.merchant.MerchantQueryRepository;
import com.example.service.merchant.MerchantCommandService;

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
public class MerchantCommandServiceImpl implements MerchantCommandService {
    private static final Logger logger = LoggerFactory.getLogger(MerchantCommandServiceImpl.class);

    MerchantCommandRepository merchantCommandRepository;
    MerchantQueryRepository merchantQueryRepository;
    UserRepository userRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public MerchantCommandServiceImpl(MerchantCommandRepository merchantCommandRepository,
            MerchantQueryRepository merchantQueryRepository,
            UserRepository userRepository,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.merchantCommandRepository = merchantCommandRepository;
        this.merchantQueryRepository = merchantQueryRepository;
        this.userRepository = userRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("merchant-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("merchant-command-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    private Uni<Void> invalidateCache(Long merchantId, Integer userId) {
        Uni<Void> u1 = merchantId != null ? redisService.deleteReactive("merchants:id:" + merchantId)
                : Uni.createFrom().voidItem();
        Uni<Void> u2 = userId != null ? redisService.deleteReactive("merchants:user:" + userId)
                : Uni.createFrom().voidItem();
        return Uni.combine().all().unis(u1, u2).discardItems();
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantResponse>> createMerchant(CreateMerchantRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createMerchant")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-service")
                .setAttribute("operation", "create_merchant")
                .setAttribute("merchant.name", req.getName())
                .startSpan();

        logger.info("Creating merchant: {}", req.getName());

        Uni<Void> userCheckUni = Uni.createFrom().voidItem();
        if (req.getUserId() != null) {
            userCheckUni = userRepository.findById(req.getUserId())
                    .chain(user -> {
                        if (user == null) {
                            logger.warn("User not found with id {}", req.getUserId());
                            throw new ResourceNotFoundException("User not found with id " + req.getUserId());
                        }
                        return Uni.createFrom().voidItem();
                    });
        }

        return userCheckUni
                .chain(() -> {
                    Merchant merchant = Merchant.fromCreateRequest(req);
                    return merchantCommandRepository.persist(merchant)
                            .chain(saved -> {
                                span.setAttribute("merchant.id", saved.id);
                                MerchantResponse response = MerchantResponse.from(saved);

                                return invalidateCache(saved.id, req.getUserId())
                                        .map(v -> {
                                            logger.info("Successfully created merchant with id: {}", saved.id);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "create_merchant",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Merchant created successfully", response);
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to create merchant: {}", req.getName(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_merchant",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_merchant"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantResponse>> updateMerchant(UpdateMerchantRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateMerchant")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-service")
                .setAttribute("operation", "update_merchant")
                .setAttribute("merchant.id", req.getMerchantId() != null ? req.getMerchantId().toString() : "null")
                .startSpan();

        logger.info("Updating merchant ID: {}", req.getMerchantId());

        if (req.getMerchantId() == null) {
            span.setStatus(StatusCode.ERROR, "MerchantId is required");
            throw new ResourceNotFoundException("MerchantId is required");
        }

        return merchantQueryRepository.findMerchantById(req.getMerchantId().longValue())
                .chain(merchant -> {
                    if (merchant == null) {
                        logger.warn("Merchant not found: {}", req.getMerchantId());
                        throw new ResourceNotFoundException("Merchant not found");
                    }

                    merchant.updateFromRequest(req);
                    return merchantCommandRepository.persist(merchant)
                            .chain(saved -> {
                                MerchantResponse response = MerchantResponse.from(saved);

                                return invalidateCache(saved.id, req.getUserId())
                                        .map(v -> {
                                            logger.info("Successfully updated merchant with id: {}", saved.id);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "update_merchant",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Merchant updated successfully", response);
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to update merchant ID: {}", req.getMerchantId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_merchant",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_merchant"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantResponseDeleteAt>> trashedMerchant(Long merchantId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashedMerchant")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-service")
                .setAttribute("operation", "trash_merchant")
                .setAttribute("merchant.id", merchantId.toString())
                .startSpan();

        logger.info("Soft deleting merchant ID: {}", merchantId);

        return merchantCommandRepository.trashed(merchantId)
                .chain(merchant -> {
                    if (merchant == null) {
                        logger.warn("Failed to trash merchant ID: {}", merchantId);
                        throw new ResourceNotFoundException("Merchant not found or already trashed");
                    }

                    MerchantResponseDeleteAt response = MerchantResponseDeleteAt.from(merchant);

                    return invalidateCache(merchantId, merchant.getUserId())
                            .map(v -> {
                                logger.info("Successfully trashed merchant with id: {}", merchantId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_merchant",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Merchant trashed successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to trash merchant ID: {}", merchantId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_merchant",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_merchant"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantResponseDeleteAt>> restoreMerchant(Long merchantId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreMerchant")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-service")
                .setAttribute("operation", "restore_merchant")
                .setAttribute("merchant.id", merchantId.toString())
                .startSpan();

        logger.info("Restoring merchant ID: {}", merchantId);

        return merchantCommandRepository.restore(merchantId)
                .chain(merchant -> {
                    if (merchant == null) {
                        logger.warn("Failed to restore merchant ID: {}", merchantId);
                        throw new ResourceNotFoundException("Merchant not found or not trashed");
                    }

                    MerchantResponseDeleteAt response = MerchantResponseDeleteAt.from(merchant);

                    return invalidateCache(merchantId, merchant.getUserId())
                            .map(v -> {
                                logger.info("Successfully restored merchant with id: {}", merchantId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_merchant",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Merchant restored successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore merchant ID: {}", merchantId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_merchant",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_merchant"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteMerchantPermanent(Long merchantId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteMerchantPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-service")
                .setAttribute("operation", "delete_merchant_permanent")
                .setAttribute("merchant.id", merchantId.toString())
                .startSpan();

        logger.warn("Permanently deleting merchant ID: {}", merchantId);

        return merchantQueryRepository.findMerchantById(merchantId)
                .chain(merchant -> {
                    if (merchant == null) {
                        logger.warn("Permanent delete failed - merchant not found with id: {}", merchantId);
                        throw new ResourceNotFoundException("Merchant not found");
                    }

                    return merchantCommandRepository.deletePermanent(merchantId)
                            .chain(deleted -> {
                                return invalidateCache(merchantId, merchant.getUserId())
                                        .map(v -> {
                                            logger.info("Successfully permanently deleted merchant with id: {}",
                                                    merchantId);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "delete_merchant_permanent",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Merchant permanently deleted", deleted);
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete merchant ID: {}", merchantId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_merchant_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_merchant_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAllMerchant() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllMerchant")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-service")
                .setAttribute("operation", "restore_all_merchants")
                .startSpan();

        logger.info("Restoring all trashed merchants");

        return merchantCommandRepository.restoreAllDeleted()
                .map(restored -> {
                    logger.info("Successfully restored all trashed merchants");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_merchants",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All trashed merchants restored", restored);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore all trashed merchants", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_merchants",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_merchants"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAllMerchantPermanent() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllMerchantPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-service")
                .setAttribute("operation", "delete_all_merchants_permanent")
                .startSpan();

        logger.warn("Permanently deleting all trashed merchants");

        return merchantCommandRepository.deleteAllDeleted()
                .map(deleted -> {
                    logger.info("Successfully permanently deleted all trashed merchants");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_merchants_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All trashed merchants permanently deleted", deleted);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete all trashed merchants", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_merchants_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_merchants_permanent"));
                });
    }
}
