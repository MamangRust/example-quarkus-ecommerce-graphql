package com.example.service.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.RedisService;
import com.example.domain.requests.merchantsociallink.CreateMerchantSocialRequest;
import com.example.domain.requests.merchantsociallink.UpdateMerchantSocialRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.merchantsociallink.MerchantSocialMediaLinkResponse;
import com.example.domain.response.merchantsociallink.MerchantSocialMediaLinkResponseDeleteAt;
import com.example.entity.merchant.MerchantSocialMediaLink;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.merchantsociallink.MerchantSocialMediaLinkRepository;
import com.example.service.MerchantSocialLinkService;

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
public class MerchantSocialLinkImplService implements MerchantSocialLinkService {
    private static final Logger logger = LoggerFactory.getLogger(MerchantSocialLinkImplService.class);

    MerchantSocialMediaLinkRepository merchantSocialMediaLinkRepository;
    Validator validator;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public MerchantSocialLinkImplService(MerchantSocialMediaLinkRepository merchantSocialMediaLinkRepository,
            Validator validator,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.merchantSocialMediaLinkRepository = merchantSocialMediaLinkRepository;
        this.validator = validator;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("merchant-social-link-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("merchant-social-link-service");

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

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantSocialMediaLinkResponse>> create(CreateMerchantSocialRequest request) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createSocialLink")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-social-link-service")
                .setAttribute("operation", "create_social_link")
                .setAttribute("merchant.detail.id",
                        request.getMerchantDetailId() != null ? request.getMerchantDetailId().toString() : "null")
                .startSpan();

        logger.info("🆕 Creating merchant social link platform={} for merchantDetailId={}",
                request.getPlatform(), request.getMerchantDetailId());

        try {
            validateRequest(request);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        return merchantSocialMediaLinkRepository.findByMerchantDetailIdAndPlatform(
                request.getMerchantDetailId(), request.getPlatform())
                .chain(optLink -> {
                    if (optLink.isPresent()) {
                        logger.warn(
                                "❌ Merchant social creation failed. Platform '{}' already exists for merchantDetailId={}",
                                request.getPlatform(), request.getMerchantDetailId());
                        throw new IllegalArgumentException("Platform '" + request.getPlatform() + "' already exists");
                    }

                    MerchantSocialMediaLink link = new MerchantSocialMediaLink();
                    link.setMerchantDetailId(request.getMerchantDetailId());
                    link.setPlatform(request.getPlatform());
                    link.setUrl(request.getUrl());
                    link.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    link.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                    return merchantSocialMediaLinkRepository.persist(link)
                            .map(saved -> {
                                span.setAttribute("social.link.id", saved.id);
                                MerchantSocialMediaLinkResponse response = MerchantSocialMediaLinkResponse.from(saved);
                                logger.info("Successfully created merchant social link with ID: {}", saved.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "create_social_link",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Merchant social link created successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to create merchant social link for detail ID: {}",
                            request.getMerchantDetailId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_social_link",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_social_link"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantSocialMediaLinkResponse>> update(UpdateMerchantSocialRequest request) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateSocialLink")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-social-link-service")
                .setAttribute("operation", "update_social_link")
                .setAttribute("social.link.id", request.getId() != null ? request.getId().toString() : "null")
                .startSpan();

        logger.info("🔄 Updating merchant social link id={}", request.getId());

        try {
            validateRequest(request);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        if (request.getId() == null) {
            span.setStatus(StatusCode.ERROR, "id is required");
            throw new ResourceNotFoundException("id is required");
        }

        return merchantSocialMediaLinkRepository.findById(request.getId().longValue())
                .chain(link -> {
                    if (link == null) {
                        logger.error("❌ Merchant social link with id {} not found", request.getId());
                        throw new ResourceNotFoundException("Merchant social link not found");
                    }

                    link.setMerchantDetailId(request.getMerchantDetailId());
                    link.setPlatform(request.getPlatform());
                    link.setUrl(request.getUrl());
                    link.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                    return merchantSocialMediaLinkRepository.persist(link)
                            .map(saved -> {
                                MerchantSocialMediaLinkResponse response = MerchantSocialMediaLinkResponse.from(saved);
                                logger.info("Successfully updated merchant social link with ID: {}", saved.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "update_social_link",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Merchant social link updated successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to update merchant social link ID: {}", request.getId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_social_link",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_social_link"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantSocialMediaLinkResponseDeleteAt>> trash(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashSocialLink")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-social-link-service")
                .setAttribute("operation", "trash_social_link")
                .setAttribute("social.link.id", id.toString())
                .startSpan();

        logger.info("🗑️ Trashing merchant social link id={}", id);

        return merchantSocialMediaLinkRepository.trashed(id.longValue())
                .map(link -> {
                    if (link == null) {
                        logger.warn("Failed to trash merchant social link - not found or already trashed ID: {}", id);
                        throw new ResourceNotFoundException("Merchant social link not found or already trashed");
                    }

                    MerchantSocialMediaLinkResponseDeleteAt response = MerchantSocialMediaLinkResponseDeleteAt
                            .from(link);
                    logger.info("Successfully trashed merchant social link with ID: {}", id);
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_social_link",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("Merchant social link trashed successfully!", response);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to trash merchant social link ID: {}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_social_link",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_social_link"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantSocialMediaLinkResponseDeleteAt>> restore(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreSocialLink")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-social-link-service")
                .setAttribute("operation", "restore_social_link")
                .setAttribute("social.link.id", id.toString())
                .startSpan();

        logger.info("♻️ Restoring merchant social link id={}", id);

        return merchantSocialMediaLinkRepository.restore(id.longValue())
                .map(link -> {
                    if (link == null) {
                        logger.warn("Failed to restore merchant social link - not found or not trashed ID: {}", id);
                        throw new ResourceNotFoundException("Merchant social link not found or not trashed");
                    }

                    MerchantSocialMediaLinkResponseDeleteAt response = MerchantSocialMediaLinkResponseDeleteAt
                            .from(link);
                    logger.info("Successfully restored merchant social link with ID: {}", id);
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_social_link",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("Merchant social link restored successfully!", response);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore merchant social link ID: {}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_social_link",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_social_link"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> delete(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteSocialLink")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-social-link-service")
                .setAttribute("operation", "delete_social_link_permanent")
                .setAttribute("social.link.id", id.toString())
                .startSpan();

        logger.warn("🧨 Permanently deleting merchant social link id={}", id);

        return merchantSocialMediaLinkRepository.deletePermanent(id.longValue())
                .map(deleted -> {
                    if (!deleted) {
                        logger.warn("Permanent delete failed - not found with ID: {}", id);
                        throw new ResourceNotFoundException("Failed to permanently delete merchant social link");
                    }

                    logger.info("Successfully permanently deleted merchant social link with ID: {}", id);
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_social_link_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("Merchant social link permanently deleted!", deleted);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete merchant social link ID: {}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_social_link_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_social_link_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllSocialLinks")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-social-link-service")
                .setAttribute("operation", "restore_all_social_links")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed merchant social links");

        return merchantSocialMediaLinkRepository.restoreAllDeleted()
                .map(restored -> {
                    logger.info("Successfully restored all trashed merchant social links");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_social_links",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All merchant social links restored successfully!", restored);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore all trashed merchant social links", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_social_links",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_social_links"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllSocialLinks")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-social-link-service")
                .setAttribute("operation", "delete_all_social_links_permanent")
                .startSpan();

        logger.warn("💣 Permanently deleting ALL trashed merchant social links");

        return merchantSocialMediaLinkRepository.deleteAllDeleted()
                .map(deleted -> {
                    logger.info("Successfully permanently deleted all trashed merchant social links");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_social_links_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All merchant social links permanently deleted!", deleted);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete all trashed merchant social links", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_social_links_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_social_links_permanent"));
                });
    }
}
