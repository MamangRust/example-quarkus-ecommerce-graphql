package com.example.service.impl.reviewdetail;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.requests.FileUpload;
import com.example.domain.requests.reviewdetail.CreateReviewDetailRequest;
import com.example.domain.requests.reviewdetail.UpdateReviewDetailRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.reviewdetail.ReviewDetailResponse;
import com.example.domain.response.reviewdetail.ReviewDetailResponseDeleteAt;
import com.example.entity.review.ReviewDetail;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.review_detail.ReviewDetailRepository;
import com.example.service.FileService;
import com.example.service.FolderService;
import com.example.service.reviewdetail.ReviewDetailService;

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
public class ReviewDetailServiceImpl implements ReviewDetailService {
    private static final Logger logger = LoggerFactory.getLogger(ReviewDetailServiceImpl.class);
    private static final String REVIEW_DETAIL_BASE_PATH = "static/review_detail";

    ReviewDetailRepository reviewDetailRepository;
    FolderService folderService;
    FileService fileService;
    Validator validator;
    OpenTelemetry openTelemetry;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public ReviewDetailServiceImpl(ReviewDetailRepository reviewDetailRepository,
            FolderService folderService,
            FileService fileService,
            Validator validator,
            OpenTelemetry openTelemetry) {
        this.reviewDetailRepository = reviewDetailRepository;
        this.folderService = folderService;
        this.fileService = fileService;
        this.validator = validator;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("review-detail-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("review-detail-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    private Uni<String> createFolderReactive(String basePath, String name) {
        return Uni.createFrom().item(() -> folderService.createFolder(basePath, name))
                .runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }

    private Uni<String> createFileImageReactive(FileUpload file, String filepath) {
        return Uni.createFrom().item(() -> fileService.createFileImage(file, filepath))
                .runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }

    private Uni<Void> deleteFileImageReactive(String filepath) {
        return Uni.createFrom().item(() -> {
            if (filepath != null) {
                fileService.deleteFileImage(filepath);
            }
            return null;
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool()).replaceWithVoid();
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
    public Uni<ApiResponse<List<ReviewDetailResponse>>> create(List<CreateReviewDetailRequest> requests) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createReviewDetails")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "create_review_details")
                .startSpan();

        try {
            validateRequest(requests);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        List<Uni<ReviewDetailResponse>> unis = new ArrayList<>();
        for (CreateReviewDetailRequest req : requests) {
            Uni<ReviewDetailResponse> itemUni = createFolderReactive(REVIEW_DETAIL_BASE_PATH, req.getReviewId().toString())
                .chain(folderPath -> {
                    if (folderPath == null) {
                        logger.warn("Failed to create folder for reviewId={}", req.getReviewId());
                        throw new RuntimeException("Failed to create folder for reviewId=" + req.getReviewId());
                    }
                    String fileName = "review_" + java.util.UUID.randomUUID().toString() + ".jpg";
                    String filePath = folderPath + java.io.File.separator + fileName;
                    return createFileImageReactive(req.getFile(), filePath);
                })
                .chain(savedPath -> {
                    if (savedPath == null) {
                        logger.warn("Failed to save file for reviewId={}", req.getReviewId());
                        throw new RuntimeException("Failed to save file for reviewId=" + req.getReviewId());
                    }
                    ReviewDetail reviewDetail = new ReviewDetail();
                    reviewDetail.setReviewId(req.getReviewId());
                    reviewDetail.setType(req.getType());
                    reviewDetail.setUrl(savedPath);
                    reviewDetail.setCaption(req.getCaption());
                    reviewDetail.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    reviewDetail.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    return reviewDetailRepository.persist(reviewDetail);
                })
                .map(ReviewDetailResponse::from);

            unis.add(itemUni);
        }

        if (unis.isEmpty()) {
            return Uni.createFrom().item(ApiResponse.success("No requests provided", new ArrayList<>()));
        }

        return Uni.join().all(unis).andCollectFailures()
                .map(responses -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_review_details",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("✅ Review details created successfully!", responses);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to create review details", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_review_details",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                    throw new RuntimeException("Failed to create review details: " + e.getMessage(), e);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_review_details"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<List<ReviewDetailResponse>>> update(List<UpdateReviewDetailRequest> requests) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateReviewDetails")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "update_review_details")
                .startSpan();

        try {
            validateRequest(requests);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        List<Uni<ReviewDetailResponse>> unis = new ArrayList<>();
        for (UpdateReviewDetailRequest req : requests) {
            Uni<ReviewDetailResponse> itemUni = reviewDetailRepository.findById(req.getReviewDetailId().longValue())
                    .chain(reviewDetail -> {
                        if (reviewDetail == null) {
                            logger.warn("Review detail not found id={}", req.getReviewDetailId());
                            throw new ResourceNotFoundException(
                                    "Review detail not found with id=" + req.getReviewDetailId());
                        }

                        Uni<Void> deleteOldImageUni = (req.getFile() != null && reviewDetail.getUrl() != null)
                                ? deleteFileImageReactive(reviewDetail.getUrl())
                                : Uni.createFrom().voidItem();

                        return deleteOldImageUni.chain(() -> {
                            if (req.getFile() != null) {
                                return createFolderReactive(REVIEW_DETAIL_BASE_PATH, reviewDetail.getReviewId().toString())
                                        .chain(folderPath -> {
                                            if (folderPath == null) {
                                                logger.error("Failed to create folder for reviewId={}", reviewDetail.getReviewId());
                                                throw new RuntimeException("Failed to create folder for reviewId=" + reviewDetail.getReviewId());
                                            }
                                            String fileName = "review_" + java.util.UUID.randomUUID().toString() + ".jpg";
                                            String filePath = folderPath + java.io.File.separator + fileName;
                                            return createFileImageReactive(req.getFile(), filePath);
                                        })
                                        .chain(savedPath -> {
                                            if (savedPath == null) {
                                                logger.error("Failed to save review detail file");
                                                throw new RuntimeException("Failed to save review detail file");
                                            }
                                            reviewDetail.setUrl(savedPath);
                                            reviewDetail.setType(req.getType());
                                            reviewDetail.setCaption(req.getCaption());
                                            reviewDetail.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                                            return reviewDetailRepository.persist(reviewDetail);
                                        });
                            } else {
                                reviewDetail.setType(req.getType());
                                reviewDetail.setCaption(req.getCaption());
                                reviewDetail.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                                return reviewDetailRepository.persist(reviewDetail);
                            }
                        });
                    })
                    .map(ReviewDetailResponse::from);

            unis.add(itemUni);
        }

        if (unis.isEmpty()) {
            return Uni.createFrom().item(ApiResponse.success("No requests provided", new ArrayList<>()));
        }

        return Uni.join().all(unis).andCollectFailures()
                .map(responses -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_review_details",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("✅ Review details updated successfully!", responses);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to update review details", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_review_details",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                    throw new RuntimeException("Failed to update review details: " + e.getMessage(), e);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_review_details"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ReviewDetailResponseDeleteAt>> trash(Integer reviewDetailId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashReviewDetail")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "trash_review_detail")
                .setAttribute("review.detail.id", reviewDetailId.toString())
                .startSpan();

        logger.info("🗑️ Trashing review detail id={}", reviewDetailId);

        return reviewDetailRepository.trashed(reviewDetailId.longValue())
                .map(trashed -> {
                    if (trashed == null) {
                        throw new ResourceNotFoundException("Review detail not found or already trashed");
                    }
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_review_detail",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("🗑️ Review detail trashed successfully!",
                            ReviewDetailResponseDeleteAt.from(trashed));
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to trash review detail id={}", reviewDetailId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_review_detail",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_review_detail"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ReviewDetailResponseDeleteAt>> restore(Integer reviewDetailId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreReviewDetail")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "restore_review_detail")
                .setAttribute("review.detail.id", reviewDetailId.toString())
                .startSpan();

        logger.info("♻️ Restoring review detail id={}", reviewDetailId);

        return reviewDetailRepository.restore(reviewDetailId.longValue())
                .map(restored -> {
                    if (restored == null) {
                        throw new ResourceNotFoundException("Review detail not found or not trashed");
                    }
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_review_detail",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("♻️ Review detail restored successfully!",
                            ReviewDetailResponseDeleteAt.from(restored));
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore review detail id={}", reviewDetailId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_review_detail",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_review_detail"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> delete(Integer reviewDetailId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteReviewDetail")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "delete_review_detail_permanent")
                .setAttribute("review.detail.id", reviewDetailId.toString())
                .startSpan();

        logger.warn("🧨 Permanently deleting review detail id={}", reviewDetailId);

        return reviewDetailRepository.findById(reviewDetailId.longValue())
                .chain(existing -> {
                    if (existing == null) {
                        throw new ResourceNotFoundException("Review detail not found");
                    }

                    Uni<Void> deleteFileUni = (existing.getUrl() != null)
                            ? deleteFileImageReactive(existing.getUrl())
                            : Uni.createFrom().voidItem();

                    return deleteFileUni.chain(() -> reviewDetailRepository.deletePermanent(reviewDetailId.longValue()));
                })
                .map(deleted -> {
                    if (!deleted) {
                        throw new ResourceNotFoundException("Failed to permanently delete review detail");
                    }
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_review_detail_permanent",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("🧨 Review detail permanently deleted!", true);
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to permanently delete review detail id={}", reviewDetailId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_review_detail_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_review_detail_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllReviewDetails")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "restore_all_review_details")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed review details");

        return reviewDetailRepository.restoreAllDeleted()
                .map(restored -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_review_details",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("🔄 All review details restored successfully!", restored);
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore all review details", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_review_details",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_review_details"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllReviewDetails")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "delete_all_review_details_permanent")
                .startSpan();

        logger.warn("💣 Permanently deleting ALL trashed review details");

        return reviewDetailRepository.deleteAllDeleted()
                .map(deleted -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_review_details_permanent",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("💣 All review details permanently deleted!", deleted);
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to delete all review details", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_review_details_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_review_details_permanent"));
                });
    }
}
