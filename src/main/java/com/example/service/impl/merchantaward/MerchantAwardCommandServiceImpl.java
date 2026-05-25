package com.example.service.impl.merchantaward;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.RedisService;
import com.example.domain.requests.merchantawrd.CreateMerchantAwardRequest;
import com.example.domain.requests.merchantawrd.UpdateMerchantAwardRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.merchantaward.MerchantAwardResponse;
import com.example.domain.response.merchantaward.MerchantAwardResponseDeleteAt;
import com.example.entity.merchant.MerchantCertificationAndAward;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.merchant.MerchantQueryRepository;
import com.example.repository.merchantaward.MerchantAwardCommandRepository;
import com.example.service.merchantaward.MerchantAwardCommandService;

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
public class MerchantAwardCommandServiceImpl implements MerchantAwardCommandService {
    private static final Logger logger = LoggerFactory.getLogger(MerchantAwardCommandServiceImpl.class);

    MerchantQueryRepository merchantQueryRepository;
    MerchantAwardCommandRepository merchantAwardCommandRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public MerchantAwardCommandServiceImpl(MerchantQueryRepository merchantQueryRepository,
            MerchantAwardCommandRepository merchantAwardCommandRepository,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.merchantQueryRepository = merchantQueryRepository;
        this.merchantAwardCommandRepository = merchantAwardCommandRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("merchant-award-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("merchant-award-command-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    private Uni<Void> invalidateCache(Long awardId) {
        if (awardId != null) {
            return redisService.deleteReactive("merchantawards:id:" + awardId);
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantAwardResponse>> createMerchantAward(CreateMerchantAwardRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createMerchantAward")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-award-service")
                .setAttribute("operation", "create_award")
                .setAttribute("award.title", req.getTitle())
                .startSpan();

        logger.info("Creating merchant award: {}", req.getTitle());

        return merchantQueryRepository.findMerchantById(req.getMerchantId().longValue())
                .chain(merchant -> {
                    if (merchant == null) {
                        logger.warn("Merchant not found with id {}", req.getMerchantId());
                        throw new ResourceNotFoundException("Merchant not found with id " + req.getMerchantId());
                    }

                    MerchantCertificationAndAward award = MerchantCertificationAndAward.fromCreateRequest(req);
                    return merchantAwardCommandRepository.persist(award)
                            .chain(saved -> {
                                span.setAttribute("award.id", saved.id);
                                MerchantAwardResponse response = MerchantAwardResponse.from(saved);

                                return invalidateCache(saved.id)
                                        .map(v -> {
                                            logger.info("Successfully created merchant award with id: {}", saved.id);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "create_award",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Merchant award created successfully", response);
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to create merchant award: {}", req.getTitle(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_award",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_award"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantAwardResponse>> updateMerchantAward(UpdateMerchantAwardRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateMerchantAward")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-award-service")
                .setAttribute("operation", "update_award")
                .setAttribute("award.id",
                        req.getMerchantCertificationId() != null ? req.getMerchantCertificationId().toString() : "null")
                .startSpan();

        logger.info("Updating merchant award ID: {}", req.getMerchantCertificationId());

        if (req.getMerchantCertificationId() == null) {
            span.setStatus(StatusCode.ERROR, "MerchantCertificationId is required");
            throw new ResourceNotFoundException("MerchantCertificationId is required");
        }

        return merchantAwardCommandRepository.findById(req.getMerchantCertificationId().longValue())
                .chain(award -> {
                    if (award == null) {
                        logger.warn("Merchant award not found: {}", req.getMerchantCertificationId());
                        throw new ResourceNotFoundException(
                                "Merchant award not found with id " + req.getMerchantCertificationId());
                    }

                    award.updateFromRequest(req);
                    return merchantAwardCommandRepository.persist(award)
                            .chain(saved -> {
                                MerchantAwardResponse response = MerchantAwardResponse.from(saved);

                                return invalidateCache(saved.id)
                                        .map(v -> {
                                            logger.info("Successfully updated merchant award with id: {}", saved.id);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "update_award",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Merchant award updated successfully", response);
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to update merchant award ID: {}", req.getMerchantCertificationId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_award",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_award"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantAwardResponseDeleteAt>> trashedMerchantAward(Long merchantAwardId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashedMerchantAward")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-award-service")
                .setAttribute("operation", "trash_award")
                .setAttribute("award.id", merchantAwardId.toString())
                .startSpan();

        logger.info("Soft deleting merchant award ID: {}", merchantAwardId);

        return merchantAwardCommandRepository.trashed(merchantAwardId)
                .chain(award -> {
                    if (award == null) {
                        logger.warn("Failed to trash merchant award ID: {}", merchantAwardId);
                        throw new ResourceNotFoundException("Merchant award not found or already trashed");
                    }

                    MerchantAwardResponseDeleteAt response = MerchantAwardResponseDeleteAt.from(award);

                    return invalidateCache(merchantAwardId)
                            .map(v -> {
                                logger.info("Successfully trashed merchant award with id: {}", merchantAwardId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_award",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Merchant award trashed successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to trash merchant award ID: {}", merchantAwardId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_award",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_award"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantAwardResponseDeleteAt>> restoreMerchantAward(Long merchantAwardId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreMerchantAward")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-award-service")
                .setAttribute("operation", "restore_award")
                .setAttribute("award.id", merchantAwardId.toString())
                .startSpan();

        logger.info("Restoring merchant award ID: {}", merchantAwardId);

        return merchantAwardCommandRepository.restore(merchantAwardId)
                .chain(award -> {
                    if (award == null) {
                        logger.warn("Failed to restore merchant award ID: {}", merchantAwardId);
                        throw new ResourceNotFoundException("Merchant award not found or not trashed");
                    }

                    MerchantAwardResponseDeleteAt response = MerchantAwardResponseDeleteAt.from(award);

                    return invalidateCache(merchantAwardId)
                            .map(v -> {
                                logger.info("Successfully restored merchant award with id: {}", merchantAwardId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_award",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Merchant award restored successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore merchant award ID: {}", merchantAwardId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_award",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_award"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteMerchantAwardPermanent(Long merchantAwardId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteMerchantAwardPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-award-service")
                .setAttribute("operation", "delete_award_permanent")
                .setAttribute("award.id", merchantAwardId.toString())
                .startSpan();

        logger.warn("Permanently deleting merchant award ID: {}", merchantAwardId);

        return merchantAwardCommandRepository.findById(merchantAwardId)
                .chain(award -> {
                    if (award == null) {
                        logger.warn("Permanent delete failed - award not found with id: {}", merchantAwardId);
                        throw new ResourceNotFoundException("Merchant award not found");
                    }

                    return merchantAwardCommandRepository.deletePermanent(merchantAwardId)
                            .chain(deleted -> {
                                return invalidateCache(merchantAwardId)
                                        .map(v -> {
                                            logger.info("Successfully permanently deleted merchant award with id: {}",
                                                    merchantAwardId);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "delete_award_permanent",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Merchant award permanently deleted", deleted);
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete merchant award ID: {}", merchantAwardId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_award_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_award_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAllMerchantAward() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllMerchantAward")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-award-service")
                .setAttribute("operation", "restore_all_awards")
                .startSpan();

        logger.info("Restoring all trashed merchant awards");

        return merchantAwardCommandRepository.restoreAllDeleted()
                .map(restored -> {
                    logger.info("Successfully restored all trashed merchant awards");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_awards",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All trashed merchant awards restored", restored);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore all trashed merchant awards", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_awards",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_awards"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAllMerchantAwardPermanent() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllMerchantAwardPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-award-service")
                .setAttribute("operation", "delete_all_awards_permanent")
                .startSpan();

        logger.warn("Permanently deleting all trashed merchant awards");

        return merchantAwardCommandRepository.deleteAllDeleted()
                .map(deleted -> {
                    logger.info("Successfully permanently deleted all trashed merchant awards");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_awards_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All trashed merchant awards permanently deleted", deleted);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete all trashed merchant awards", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_awards_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_awards_permanent"));
                });
    }
}
