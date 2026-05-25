package com.example.service.impl.merchantpolicy;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.RedisService;
import com.example.domain.requests.merchantpolicy.CreateMerchantPolicyRequest;
import com.example.domain.requests.merchantpolicy.UpdateMerchantPolicyRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.merchantpolicy.MerchantPoliciesResponse;
import com.example.domain.response.merchantpolicy.MerchantPoliciesResponseDeleteAt;
import com.example.entity.merchant.MerchantPolicy;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.merchant.MerchantQueryRepository;
import com.example.repository.merchantpolicy.MerchantPolicyCommandRepository;
import com.example.service.merchantpolicy.MerchantPolicyCommandService;

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
public class MerchantPolicyCommandServiceImpl implements MerchantPolicyCommandService {
    private static final Logger logger = LoggerFactory.getLogger(MerchantPolicyCommandServiceImpl.class);

    MerchantQueryRepository merchantQueryRepository;
    MerchantPolicyCommandRepository merchantPolicyCommandRepository;
    Validator validator;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public MerchantPolicyCommandServiceImpl(MerchantQueryRepository merchantQueryRepository,
            MerchantPolicyCommandRepository merchantPolicyCommandRepository,
            Validator validator,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.merchantQueryRepository = merchantQueryRepository;
        this.merchantPolicyCommandRepository = merchantPolicyCommandRepository;
        this.validator = validator;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("merchant-policy-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("merchant-policy-command-service");

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

    private Uni<Void> invalidateCache(Long policyId) {
        if (policyId != null) {
            return redisService.deleteReactive("merchantpolicy:id:" + policyId);
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantPoliciesResponse>> create(CreateMerchantPolicyRequest request) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createMerchantPolicy")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-policy-service")
                .setAttribute("operation", "create_policy")
                .setAttribute("merchant.id",
                        request.getMerchantId() != null ? request.getMerchantId().toString() : "null")
                .startSpan();

        logger.info("🆕 Creating merchant policy for merchantId={} title={}", request.getMerchantId(),
                request.getTitle());

        try {
            validateRequest(request);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        return merchantQueryRepository.findMerchantById(request.getMerchantId().longValue())
                .chain(merchant -> {
                    if (merchant == null) {
                        logger.warn("Merchant not found with id {}", request.getMerchantId());
                        throw new ResourceNotFoundException("Merchant not found with id " + request.getMerchantId());
                    }

                    MerchantPolicy policy = new MerchantPolicy();
                    policy.setMerchantId(request.getMerchantId());
                    policy.setPolicyType(request.getPolicyType());
                    policy.setTitle(request.getTitle());
                    policy.setDescription(request.getDescription());
                    policy.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    policy.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                    return merchantPolicyCommandRepository.persist(policy)
                            .chain(saved -> {
                                span.setAttribute("policy.id", saved.id);
                                MerchantPoliciesResponse response = MerchantPoliciesResponse.from(saved);

                                return invalidateCache(saved.id)
                                        .map(v -> {
                                            logger.info("Successfully created merchant policy with ID: {}", saved.id);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "create_policy",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Merchant policy created successfully!",
                                                    response);
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to create merchant policy for merchant ID: {}", request.getMerchantId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_policy",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_policy"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantPoliciesResponse>> update(UpdateMerchantPolicyRequest request) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateMerchantPolicy")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-policy-service")
                .setAttribute("operation", "update_policy")
                .setAttribute("policy.id",
                        request.getMerchantPolicyId() != null ? request.getMerchantPolicyId().toString() : "null")
                .startSpan();

        logger.info("🔄 Updating merchant policy id={}", request.getMerchantPolicyId());

        try {
            validateRequest(request);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        if (request.getMerchantPolicyId() == null) {
            span.setStatus(StatusCode.ERROR, "MerchantPolicyId is required");
            throw new ResourceNotFoundException("MerchantPolicyId is required");
        }

        return merchantPolicyCommandRepository.findById(request.getMerchantPolicyId().longValue())
                .chain(policy -> {
                    if (policy == null) {
                        logger.warn("Merchant policy not found: {}", request.getMerchantPolicyId());
                        throw new ResourceNotFoundException(
                                "Merchant policy not found with id " + request.getMerchantPolicyId());
                    }

                    policy.setPolicyType(request.getPolicyType());
                    policy.setTitle(request.getTitle());
                    policy.setDescription(request.getDescription());
                    policy.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                    return merchantPolicyCommandRepository.persist(policy)
                            .chain(saved -> {
                                MerchantPoliciesResponse response = MerchantPoliciesResponse.from(saved);

                                return invalidateCache(saved.id)
                                        .map(v -> {
                                            logger.info("Successfully updated merchant policy with ID: {}", saved.id);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "update_policy",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Merchant policy updated successfully!",
                                                    response);
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to update merchant policy ID: {}", request.getMerchantPolicyId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_policy",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_policy"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantPoliciesResponseDeleteAt>> trash(Long id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashMerchantPolicy")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-policy-service")
                .setAttribute("operation", "trash_policy")
                .setAttribute("policy.id", id.toString())
                .startSpan();

        logger.info("🗑️ Trashing merchant policy id={}", id);

        return merchantPolicyCommandRepository.trashed(id)
                .chain(policy -> {
                    if (policy == null) {
                        logger.warn("Failed to trash merchant policy - not found or already trashed with ID: {}", id);
                        throw new ResourceNotFoundException("Merchant policy not found or already trashed");
                    }

                    MerchantPoliciesResponseDeleteAt response = MerchantPoliciesResponseDeleteAt.from(policy);

                    return invalidateCache(id)
                            .map(v -> {
                                logger.info("Successfully trashed merchant policy with ID: {}", id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_policy",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Merchant policy trashed successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to trash merchant policy ID: {}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_policy",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_policy"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantPoliciesResponseDeleteAt>> restore(Long id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreMerchantPolicy")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-policy-service")
                .setAttribute("operation", "restore_policy")
                .setAttribute("policy.id", id.toString())
                .startSpan();

        logger.info("♻️ Restoring merchant policy id={}", id);

        return merchantPolicyCommandRepository.restore(id)
                .chain(policy -> {
                    if (policy == null) {
                        logger.warn("Failed to restore merchant policy - not found or not trashed with ID: {}", id);
                        throw new ResourceNotFoundException("Merchant policy not found or not trashed");
                    }

                    MerchantPoliciesResponseDeleteAt response = MerchantPoliciesResponseDeleteAt.from(policy);

                    return invalidateCache(id)
                            .map(v -> {
                                logger.info("Successfully restored merchant policy with ID: {}", id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_policy",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Merchant policy restored successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore merchant policy ID: {}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_policy",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_policy"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> delete(Long id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteMerchantPolicy")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-policy-service")
                .setAttribute("operation", "delete_policy_permanent")
                .setAttribute("policy.id", id.toString())
                .startSpan();

        logger.warn("🧨 Permanently deleting merchant policy id={}", id);

        return merchantPolicyCommandRepository.findById(id)
                .chain(policy -> {
                    if (policy == null) {
                        logger.warn("Permanent delete failed - not found with ID: {}", id);
                        throw new ResourceNotFoundException("Merchant policy not found");
                    }

                    return merchantPolicyCommandRepository.deletePermanent(id)
                            .chain(deleted -> {
                                return invalidateCache(id)
                                        .map(v -> {
                                            logger.info("Successfully permanently deleted merchant policy with ID: {}",
                                                    id);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "delete_policy_permanent",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Merchant policy permanently deleted!", deleted);
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete merchant policy ID: {}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_policy_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_policy_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllMerchantPolicies")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-policy-service")
                .setAttribute("operation", "restore_all_policies")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed merchant policies");

        return merchantPolicyCommandRepository.restoreAllDeleted()
                .map(restored -> {
                    logger.info("Successfully restored all trashed merchant policies");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_policies",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All merchant policies restored successfully!", restored);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore all trashed merchant policies", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_policies",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_policies"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllMerchantPolicies")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-policy-service")
                .setAttribute("operation", "delete_all_policies_permanent")
                .startSpan();

        logger.warn("💣 Permanently deleting ALL trashed merchant policies");

        return merchantPolicyCommandRepository.deleteAllDeleted()
                .map(deleted -> {
                    logger.info("Successfully permanently deleted all trashed merchant policies");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_policies_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All merchant policies permanently deleted!", deleted);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete all trashed merchant policies", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_policies_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_policies_permanent"));
                });
    }
}
