package com.example.service.impl.merchantbusiness;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.RedisService;
import com.example.domain.requests.merchantbusiness.CreateMerchantBusinessRequest;
import com.example.domain.requests.merchantbusiness.UpdateMerchantBusinessRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.merchantbusiness.MerchantBusinessResponse;
import com.example.domain.response.merchantbusiness.MerchantBusinessResponseDeleteAt;
import com.example.entity.merchant.MerchantBusinessInformation;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.merchant.MerchantQueryRepository;
import com.example.repository.merchantbusiness.MerchantBusinessCommandRepository;
import com.example.repository.merchantbusiness.MerchantBusinessQueryRepository;
import com.example.service.merchantbusiness.MerchantBusinessCommandService;

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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@ApplicationScoped
public class MerchantBusinessCommandServiceImpl implements MerchantBusinessCommandService {
    private static final Logger logger = LoggerFactory.getLogger(MerchantBusinessCommandServiceImpl.class);

    MerchantBusinessCommandRepository merchantBusinessCommandRepository;
    MerchantBusinessQueryRepository merchantBusinessQueryRepository;
    MerchantQueryRepository merchantQueryRepository;
    Validator validator;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public MerchantBusinessCommandServiceImpl(MerchantBusinessCommandRepository merchantBusinessCommandRepository,
            MerchantBusinessQueryRepository merchantBusinessQueryRepository,
            MerchantQueryRepository merchantQueryRepository,
            Validator validator,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.merchantBusinessCommandRepository = merchantBusinessCommandRepository;
        this.merchantBusinessQueryRepository = merchantBusinessQueryRepository;
        this.merchantQueryRepository = merchantQueryRepository;
        this.validator = validator;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("merchant-business-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("merchant-business-command-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    private <T> void validateRequest(T req) {
        Set<ConstraintViolation<T>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ConstraintViolation<T> violation : violations) {
                sb.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("; ");
            }
            logger.error("Validation failed: {}", sb);
            throw new ConstraintViolationException("Validation failed: " + sb, violations);
        }
    }

    private Uni<Void> invalidateCache(Long businessId) {
        if (businessId != null) {
            return redisService.deleteReactive("merchantbusiness:id:" + businessId);
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantBusinessResponse>> createMerchantBusiness(CreateMerchantBusinessRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createMerchantBusiness")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-business-service")
                .setAttribute("operation", "create_business")
                .setAttribute("merchant.id", req.getMerchantId() != null ? req.getMerchantId().toString() : "null")
                .startSpan();

        logger.info("Creating merchant business info for merchant ID: {}", req.getMerchantId());

        try {
            validateRequest(req);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        return merchantQueryRepository.findMerchantById(req.getMerchantId().longValue())
                .chain(merchant -> {
                    if (merchant == null) {
                        logger.warn("Merchant not found with id {}", req.getMerchantId());
                        throw new ResourceNotFoundException("Merchant not found with id " + req.getMerchantId());
                    }

                    MerchantBusinessInformation business = MerchantBusinessInformation.fromCreateRequest(req);
                    return merchantBusinessCommandRepository.persist(business)
                            .chain(saved -> {
                                span.setAttribute("business.id", saved.id);
                                MerchantBusinessResponse response = MerchantBusinessResponse.from(saved);

                                return invalidateCache(saved.id)
                                        .map(v -> {
                                            logger.info("Successfully created merchant business info with id: {}",
                                                    saved.id);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "create_business",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Merchant business info created successfully",
                                                    response);
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to create merchant business info for merchant ID: {}", req.getMerchantId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_business",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_business"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantBusinessResponse>> updateMerchantBusiness(UpdateMerchantBusinessRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateMerchantBusiness")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-business-service")
                .setAttribute("operation", "update_business")
                .setAttribute("business.id",
                        req.getMerchantBusinessInfoId() != null ? req.getMerchantBusinessInfoId().toString() : "null")
                .startSpan();

        logger.info("Updating merchant business info ID: {}", req.getMerchantBusinessInfoId());

        try {
            validateRequest(req);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        if (req.getMerchantBusinessInfoId() == null) {
            span.setStatus(StatusCode.ERROR, "MerchantBusinessInfoId is required");
            throw new ResourceNotFoundException("MerchantBusinessInfoId is required");
        }

        return merchantBusinessQueryRepository
                .findMerchantBusinessInformationById(req.getMerchantBusinessInfoId().longValue())
                .chain(business -> {
                    if (business == null) {
                        logger.warn("Merchant business info not found: {}", req.getMerchantBusinessInfoId());
                        throw new ResourceNotFoundException(
                                "Merchant business info not found with id " + req.getMerchantBusinessInfoId());
                    }

                    business.setBusinessType(req.getBusinessType());
                    business.setTaxId(req.getTaxId());
                    business.setEstablishedYear(req.getEstablishedYear());
                    business.setNumberOfEmployees(req.getNumberOfEmployees());
                    business.setWebsiteUrl(req.getWebsiteUrl());

                    return merchantBusinessCommandRepository.persist(business)
                            .chain(saved -> {
                                MerchantBusinessResponse response = MerchantBusinessResponse.from(saved);

                                return invalidateCache(saved.id)
                                        .map(v -> {
                                            logger.info("Successfully updated merchant business info with id: {}",
                                                    saved.id);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "update_business",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Merchant business info updated successfully",
                                                    response);
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to update merchant business info ID: {}", req.getMerchantBusinessInfoId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_business",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_business"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantBusinessResponseDeleteAt>> trashedMerchantBusiness(Long merchantBusinessInfoId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashedMerchantBusiness")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-business-service")
                .setAttribute("operation", "trash_business")
                .setAttribute("business.id", merchantBusinessInfoId.toString())
                .startSpan();

        logger.info("Soft deleting merchant business info ID: {}", merchantBusinessInfoId);

        return merchantBusinessCommandRepository.trashed(merchantBusinessInfoId)
                .chain(business -> {
                    if (business == null) {
                        logger.warn("Failed to trash merchant business info ID: {}", merchantBusinessInfoId);
                        throw new ResourceNotFoundException("Merchant business info not found or already trashed");
                    }

                    MerchantBusinessResponseDeleteAt response = MerchantBusinessResponseDeleteAt.from(business);

                    return invalidateCache(merchantBusinessInfoId)
                            .map(v -> {
                                logger.info("Successfully trashed merchant business info with id: {}",
                                        merchantBusinessInfoId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_business",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Merchant business info trashed successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to trash merchant business info ID: {}", merchantBusinessInfoId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_business",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_business"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantBusinessResponseDeleteAt>> restoreMerchantBusiness(Long merchantBusinessInfoId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreMerchantBusiness")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-business-service")
                .setAttribute("operation", "restore_business")
                .setAttribute("business.id", merchantBusinessInfoId.toString())
                .startSpan();

        logger.info("Restoring merchant business info ID: {}", merchantBusinessInfoId);

        return merchantBusinessCommandRepository.restore(merchantBusinessInfoId)
                .chain(business -> {
                    if (business == null) {
                        logger.warn("Failed to restore merchant business info ID: {}", merchantBusinessInfoId);
                        throw new ResourceNotFoundException("Merchant business info not found or not trashed");
                    }

                    MerchantBusinessResponseDeleteAt response = MerchantBusinessResponseDeleteAt.from(business);

                    return invalidateCache(merchantBusinessInfoId)
                            .map(v -> {
                                logger.info("Successfully restored merchant business info with id: {}",
                                        merchantBusinessInfoId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_business",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Merchant business info restored successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore merchant business info ID: {}", merchantBusinessInfoId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_business",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_business"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteMerchantBusinessPermanent(Long merchantBusinessInfoId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteMerchantBusinessPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-business-service")
                .setAttribute("operation", "delete_business_permanent")
                .setAttribute("business.id", merchantBusinessInfoId.toString())
                .startSpan();

        logger.warn("Permanently deleting merchant business info ID: {}", merchantBusinessInfoId);

        return merchantBusinessQueryRepository.findById(merchantBusinessInfoId)
                .chain(business -> {
                    if (business == null) {
                        logger.warn("Permanent delete failed - business info not found with id: {}",
                                merchantBusinessInfoId);
                        throw new ResourceNotFoundException("Merchant business info not found");
                    }

                    return merchantBusinessCommandRepository.deletePermanent(merchantBusinessInfoId)
                            .chain(deleted -> {
                                return invalidateCache(merchantBusinessInfoId)
                                        .map(v -> {
                                            logger.info(
                                                    "Successfully permanently deleted merchant business info with id: {}",
                                                    merchantBusinessInfoId);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "delete_business_permanent",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Merchant business info permanently deleted",
                                                    deleted);
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete merchant business info ID: {}", merchantBusinessInfoId,
                            e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_business_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_business_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAllMerchantBusiness() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllMerchantBusiness")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-business-service")
                .setAttribute("operation", "restore_all_business_info")
                .startSpan();

        logger.info("Restoring all trashed merchant business info");

        return merchantBusinessCommandRepository.restoreAllDeleted()
                .map(restored -> {
                    logger.info("Successfully restored all trashed merchant business info");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_business_info",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All trashed merchant business info restored", restored);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore all trashed merchant business info", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_business_info",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_business_info"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAllMerchantBusinessPermanent() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllMerchantBusinessPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-business-service")
                .setAttribute("operation", "delete_all_business_info_permanent")
                .startSpan();

        logger.warn("Permanently deleting all trashed merchant business info");

        return merchantBusinessCommandRepository.deleteAllDeleted()
                .map(deleted -> {
                    logger.info("Successfully permanently deleted all trashed merchant business info");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_business_info_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All trashed merchant business info permanently deleted", deleted);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete all trashed merchant business info", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_business_info_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_business_info_permanent"));
                });
    }
}
