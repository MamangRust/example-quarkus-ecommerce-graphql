package com.example.service.impl.review;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.RedisService;
import com.example.domain.requests.review.CreateReviewRequest;
import com.example.domain.requests.review.UpdateReviewRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.reviews.ReviewResponse;
import com.example.domain.response.reviews.ReviewResponseDeleteAt;
import com.example.entity.review.Review;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.review.ReviewCommandRepository;
import com.example.repository.review.ReviewQueryRepository;
import com.example.service.review.ReviewCommandService;

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
public class ReviewCommandServiceImpl implements ReviewCommandService {
    private static final Logger logger = LoggerFactory.getLogger(ReviewCommandServiceImpl.class);

    ReviewQueryRepository reviewQueryRepository;
    ReviewCommandRepository reviewCommandRepository;
    Validator validator;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public ReviewCommandServiceImpl(ReviewQueryRepository reviewQueryRepository,
            ReviewCommandRepository reviewCommandRepository,
            Validator validator,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.reviewQueryRepository = reviewQueryRepository;
        this.reviewCommandRepository = reviewCommandRepository;
        this.validator = validator;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("review-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("review-command-service");

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

    private Uni<Void> invalidateCache(Long reviewId) {
        if (reviewId != null) {
            return redisService.deleteReactive("review:id:" + reviewId).replaceWithVoid();
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ReviewResponse>> create(CreateReviewRequest request) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createReview")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-service")
                .setAttribute("operation", "create_review")
                .setAttribute("product.id", String.valueOf(request.getProductId()))
                .setAttribute("user.id", String.valueOf(request.getUserId()))
                .startSpan();

        logger.info("🆕 Creating review for productId={}, userId={}",
                request.getProductId(), request.getUserId());

        try {
            validateRequest(request);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        Review review = new Review();
        review.setUserId(request.getUserId());
        review.setProductId(request.getProductId());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        review.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

        return reviewCommandRepository.persist(review)
                .chain(saved -> {
                    ReviewResponse response = ReviewResponse.from(saved);
                    span.setAttribute("review.id", saved.id);

                    return invalidateCache(saved.id)
                            .map(v -> {
                                logger.info("✅ Review created successfully id={}", saved.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "create_review",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("✅ Review created successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to create review", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_review",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_review"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ReviewResponse>> update(UpdateReviewRequest request) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateReview")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-service")
                .setAttribute("operation", "update_review")
                .setAttribute("review.id", request.getReviewId() != null ? request.getReviewId().toString() : "null")
                .startSpan();

        logger.info("🔄 Updating review id={}", request.getReviewId());

        try {
            validateRequest(request);
            if (request.getReviewId() == null) {
                throw new ResourceNotFoundException("review_id is required");
            }
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        return reviewQueryRepository.findReviewById(request.getReviewId().longValue())
                .chain(optReview -> {
                    if (optReview.isEmpty()) {
                        throw new ResourceNotFoundException("Review not found");
                    }
                    Review review = optReview.get();
                    review.setComment(request.getComment());
                    review.setRating(request.getRating());
                    review.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                    return reviewCommandRepository.persist(review);
                })
                .chain(updated -> {
                    ReviewResponse response = ReviewResponse.from(updated);

                    return invalidateCache(updated.id)
                            .map(v -> {
                                logger.info("✅ Review updated successfully id={}", updated.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "update_review",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("✅ Review updated successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to update review id={}", request.getReviewId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_review",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_review"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ReviewResponseDeleteAt>> trash(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashReview")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-service")
                .setAttribute("operation", "trash_review")
                .setAttribute("review.id", id.toString())
                .startSpan();

        logger.info("🗑️ Trashing review id={}", id);

        return reviewCommandRepository.trashed(id.longValue())
                .chain(review -> {
                    if (review == null) {
                        throw new ResourceNotFoundException("Review not found or already trashed");
                    }
                    ReviewResponseDeleteAt response = ReviewResponseDeleteAt.from(review);

                    return invalidateCache(id.longValue())
                            .map(v -> {
                                logger.info("Successfully trashed review with ID: {}", id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_review",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("🗑️ Review trashed successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to trash review id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_review",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_review"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ReviewResponseDeleteAt>> restore(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreReview")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-service")
                .setAttribute("operation", "restore_review")
                .setAttribute("review.id", id.toString())
                .startSpan();

        logger.info("♻️ Restoring review id={}", id);

        return reviewCommandRepository.restore(id.longValue())
                .chain(review -> {
                    if (review == null) {
                        throw new ResourceNotFoundException("Review not found or not trashed");
                    }
                    ReviewResponseDeleteAt response = ReviewResponseDeleteAt.from(review);

                    return invalidateCache(id.longValue())
                            .map(v -> {
                                logger.info("Successfully restored review with ID: {}", id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_review",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("♻️ Review restored successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore review id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_review",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_review"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> delete(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteReviewPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-service")
                .setAttribute("operation", "delete_review_permanent")
                .setAttribute("review.id", id.toString())
                .startSpan();

        logger.warn("🧨 Permanently deleting review id={}", id);

        return reviewCommandRepository.deletePermanent(id.longValue())
                .chain(deleted -> {
                    return invalidateCache(id.longValue())
                            .map(v -> {
                                logger.info("Successfully permanently deleted review with ID: {}", id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "delete_review_permanent",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("🧨 Review permanently deleted!", deleted);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to permanently delete review id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_review_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_review_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllReviews")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-service")
                .setAttribute("operation", "restore_all_reviews")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed reviews");

        return reviewCommandRepository.restoreAllDeleted()
                .map(restored -> {
                    logger.info("Successfully restored all trashed reviews");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_reviews",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("🔄 All reviews restored successfully!", restored);
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore all reviews", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_reviews",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_reviews"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllReviewsPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-service")
                .setAttribute("operation", "delete_all_reviews_permanent")
                .startSpan();

        logger.warn("💣 Permanently deleting ALL trashed reviews");

        return reviewCommandRepository.deleteAllDeleted()
                .map(deleted -> {
                    logger.info("Successfully permanently deleted all trashed reviews");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_reviews_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("💣 All reviews permanently deleted!", deleted);
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to delete all reviews", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_reviews_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_reviews_permanent"));
                });
    }
}
