package com.example.service.impl.merchantdetail;

import java.io.File;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.RedisService;
import com.example.domain.requests.FileUpload;
import com.example.domain.requests.merchantdetail.CreateMerchantDetailRequest;
import com.example.domain.requests.merchantdetail.UpdateMerchantDetailRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.merchantdetail.MerchantDetailResponse;
import com.example.domain.response.merchantdetail.MerchantDetailResponseDeleteAt;
import com.example.entity.merchant.MerchantDetail;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.merchantdetail.MerchantDetailCommandRepository;
import com.example.repository.merchantdetail.MerchantDetailQueryRepository;
import com.example.service.FileService;
import com.example.service.FolderService;
import com.example.service.merchantdetail.MerchantDetailCommandService;

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
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MerchantDetailCommandServiceImpl implements MerchantDetailCommandService {
    private static final Logger logger = LoggerFactory.getLogger(MerchantDetailCommandServiceImpl.class);
    private static final String MERCHANT_BASE_PATH = "static/merchant-detail";

    MerchantDetailQueryRepository merchantDetailQueryRepository;
    MerchantDetailCommandRepository merchantDetailCommandRepository;
    FolderService folderService;
    FileService fileService;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public MerchantDetailCommandServiceImpl(MerchantDetailQueryRepository merchantDetailQueryRepository,
            MerchantDetailCommandRepository merchantDetailCommandRepository,
            FolderService folderService,
            FileService fileService,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.merchantDetailQueryRepository = merchantDetailQueryRepository;
        this.merchantDetailCommandRepository = merchantDetailCommandRepository;
        this.folderService = folderService;
        this.fileService = fileService;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("merchant-detail-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("merchant-detail-command-service");

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
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Uni<String> createFileImageReactive(FileUpload file, String filepath) {
        return Uni.createFrom().item(() -> fileService.createFileImage(file, filepath))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Uni<Void> deleteFileImageReactive(String filepath) {
        return Uni.createFrom().item(() -> {
            if (filepath != null) {
                fileService.deleteFileImage(filepath);
            }
            return null;
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool()).replaceWithVoid();
    }

    private Uni<Void> invalidateCache(Long merchantDetailId) {
        if (merchantDetailId != null) {
            return redisService.deleteReactive("merchantdetail:id:" + merchantDetailId);
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantDetailResponse>> createMerchant(CreateMerchantDetailRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createMerchantDetail")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-detail-service")
                .setAttribute("operation", "create_detail")
                .setAttribute("merchant.id", req.getMerchantId() != null ? req.getMerchantId().toString() : "null")
                .startSpan();

        logger.info("🆕 Creating merchant detail: {}", req);

        return createFolderReactive(MERCHANT_BASE_PATH, "merchant_" + req.getMerchantId())
                .chain(folderPath -> {
                    if (folderPath == null) {
                        logger.error("Failed to create folder for merchant detail");
                        throw new RuntimeException("Failed to create folder for merchant");
                    }

                    String coverPath = folderPath + File.separator + "cover.jpg";
                    String logoPath = folderPath + File.separator + "logo.jpg";

                    return Uni.combine().all().unis(
                            createFileImageReactive(req.getCoverImageUrl(), coverPath),
                            createFileImageReactive(req.getLogoUrl(), logoPath)).asTuple().chain(tuple -> {
                                String savedCover = tuple.getItem1();
                                String savedLogo = tuple.getItem2();

                                if (savedCover == null || savedLogo == null) {
                                    logger.error("Failed to save cover or logo images");
                                    throw new RuntimeException("Failed to save cover or logo images");
                                }

                                MerchantDetail entity = new MerchantDetail();
                                entity.setMerchantId(req.getMerchantId());
                                entity.setDisplayName(req.getDisplayName());
                                entity.setShortDescription(req.getShortDescription());
                                entity.setWebsiteUrl(req.getWebsiteUrl());
                                entity.setCoverImageUrl(savedCover);
                                entity.setLogoUrl(savedLogo);

                                return merchantDetailCommandRepository.persist(entity)
                                        .chain(saved -> {
                                            MerchantDetailResponse response = MerchantDetailResponse.from(saved);
                                            span.setAttribute("detail.id", saved.id);

                                            return invalidateCache(saved.id)
                                                    .map(v -> {
                                                        logger.info("Successfully created merchant detail with ID: {}",
                                                                saved.id);
                                                        span.setStatus(StatusCode.OK);

                                                        requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"), "create_detail",
                                                                AttributeKey.stringKey("status"), "success"));

                                                        return ApiResponse.success(
                                                                "Merchant detail created successfully!", response);
                                                    });
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to create merchant detail for merchant: {}", req.getMerchantId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_detail",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_detail"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantDetailResponse>> updateMerchant(UpdateMerchantDetailRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateMerchantDetail")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-detail-service")
                .setAttribute("operation", "update_detail")
                .setAttribute("detail.id",
                        req.getMerchantDetailId() != null ? req.getMerchantDetailId().toString() : "null")
                .startSpan();

        logger.info("✏️ Updating merchant detail id={}", req.getMerchantDetailId());

        if (req.getMerchantDetailId() == null) {
            span.setStatus(StatusCode.ERROR, "MerchantDetailId is required");
            return Uni.createFrom().failure(new ResourceNotFoundException("MerchantDetailId is required"));
        }

        return merchantDetailQueryRepository.findById(req.getMerchantDetailId().longValue())
                .chain(existing -> {
                    if (existing == null) {
                        logger.warn("Merchant detail not found with id {}", req.getMerchantDetailId());
                        throw new ResourceNotFoundException(
                                "Merchant detail not found with id " + req.getMerchantDetailId());
                    }

                    // Delete existing images first
                    return Uni.combine().all().unis(
                            deleteFileImageReactive(existing.getCoverImageUrl()),
                            deleteFileImageReactive(existing.getLogoUrl())).discardItems().chain(() -> {
                                return createFolderReactive(MERCHANT_BASE_PATH, "merchant_" + existing.getMerchantId())
                                        .chain(folderPath -> {
                                            if (folderPath == null) {
                                                logger.error("Failed to create folder for merchant detail");
                                                throw new RuntimeException("Failed to create folder for merchant");
                                            }

                                            String coverPath = folderPath + File.separator
                                                    + "cover.jpg";
                                            String logoPath = folderPath + File.separator + "logo.jpg";

                                            return Uni.combine().all().unis(
                                                    createFileImageReactive(req.getCoverImageUrl(), coverPath),
                                                    createFileImageReactive(req.getLogoUrl(), logoPath)).asTuple()
                                                    .chain(tuple -> {
                                                        String savedCover = tuple.getItem1();
                                                        String savedLogo = tuple.getItem2();

                                                        if (savedCover == null || savedLogo == null) {
                                                            logger.error("Failed to save cover or logo images");
                                                            throw new RuntimeException(
                                                                    "Failed to save cover or logo images");
                                                        }

                                                        existing.setDisplayName(req.getDisplayName());
                                                        existing.setShortDescription(req.getShortDescription());
                                                        existing.setWebsiteUrl(req.getWebsiteUrl());
                                                        existing.setCoverImageUrl(savedCover);
                                                        existing.setLogoUrl(savedLogo);

                                                        return merchantDetailCommandRepository.persist(existing)
                                                                .chain(saved -> {
                                                                    MerchantDetailResponse response = MerchantDetailResponse
                                                                            .from(saved);

                                                                    return invalidateCache(saved.id)
                                                                            .map(v -> {
                                                                                logger.info(
                                                                                        "Successfully updated merchant detail with ID: {}",
                                                                                        saved.id);
                                                                                span.setStatus(StatusCode.OK);

                                                                                requestsTotal.add(1, Attributes.of(
                                                                                        AttributeKey
                                                                                                .stringKey("operation"),
                                                                                        "update_detail",
                                                                                        AttributeKey.stringKey(
                                                                                                "status"),
                                                                                        "success"));

                                                                                return ApiResponse.success(
                                                                                        "Merchant detail updated successfully!",
                                                                                        response);
                                                                            });
                                                                });
                                                    });
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to update merchant detail ID: {}", req.getMerchantDetailId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_detail",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_detail"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantDetailResponseDeleteAt>> trashedMerchant(Long merchantID) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashedMerchantDetail")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-detail-service")
                .setAttribute("operation", "trash_detail")
                .setAttribute("detail.id", merchantID.toString())
                .startSpan();

        logger.info("🗑️ Trashing merchant detail id={}", merchantID);

        return merchantDetailCommandRepository.trashed(merchantID)
                .chain(detail -> {
                    if (detail == null) {
                        logger.warn("Failed to trash merchant detail - not found with ID: {}", merchantID);
                        throw new ResourceNotFoundException("Merchant detail not found or already trashed");
                    }

                    MerchantDetailResponseDeleteAt response = MerchantDetailResponseDeleteAt.from(detail);

                    return invalidateCache(merchantID)
                            .map(v -> {
                                logger.info("Successfully trashed merchant detail with ID: {}", merchantID);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_detail",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Merchant detail trashed successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to trash merchant detail ID: {}", merchantID, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_detail",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_detail"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantDetailResponseDeleteAt>> restoreMerchant(Long merchantID) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreMerchantDetail")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-detail-service")
                .setAttribute("operation", "restore_detail")
                .setAttribute("detail.id", merchantID.toString())
                .startSpan();

        logger.info("♻️ Restoring merchant detail id={}", merchantID);

        return merchantDetailCommandRepository.restore(merchantID)
                .chain(detail -> {
                    if (detail == null) {
                        logger.warn("Failed to restore merchant detail - not found with ID: {}", merchantID);
                        throw new ResourceNotFoundException("Merchant detail not found or not trashed");
                    }

                    MerchantDetailResponseDeleteAt response = MerchantDetailResponseDeleteAt.from(detail);

                    return invalidateCache(merchantID)
                            .map(v -> {
                                logger.info("Successfully restored merchant detail with ID: {}", merchantID);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_detail",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Merchant detail restored successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore merchant detail ID: {}", merchantID, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_detail",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_detail"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteMerchantPermanent(Long merchantID) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteMerchantPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-detail-service")
                .setAttribute("operation", "delete_detail_permanent")
                .setAttribute("detail.id", merchantID.toString())
                .startSpan();

        logger.warn("🗑️ Permanently deleting merchant detail id={}", merchantID);

        return merchantDetailQueryRepository.findById(merchantID)
                .chain(existing -> {
                    if (existing == null) {
                        logger.warn("Failed to delete permanently - not found with ID: {}", merchantID);
                        throw new ResourceNotFoundException("Merchant detail not found");
                    }

                    return Uni.combine().all().unis(
                            deleteFileImageReactive(existing.getCoverImageUrl()),
                            deleteFileImageReactive(existing.getLogoUrl())).discardItems().chain(() -> {
                                return merchantDetailCommandRepository.deletePermanent(merchantID)
                                        .chain(deleted -> {
                                            return invalidateCache(merchantID)
                                                    .map(v -> {
                                                        logger.info(
                                                                "Successfully permanently deleted merchant detail with ID: {}",
                                                                merchantID);
                                                        span.setStatus(StatusCode.OK);

                                                        requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"),
                                                                "delete_detail_permanent",
                                                                AttributeKey.stringKey("status"), "success"));

                                                        return ApiResponse.success(
                                                                "Merchant detail permanently deleted", deleted);
                                                    });
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete merchant detail ID: {}", merchantID, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_detail_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_detail_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAllMerchant() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllMerchant")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-detail-service")
                .setAttribute("operation", "restore_all_details")
                .startSpan();

        logger.info("♻️ Restoring all trashed merchant details");

        return merchantDetailCommandRepository.restoreAllDeleted()
                .map(restored -> {
                    logger.info("Successfully restored all trashed merchant details");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_details",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All trashed merchant details restored", restored);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore all trashed merchant details", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_details",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_details"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAllMerchantPermanent() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllMerchantPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "merchant-detail-service")
                .setAttribute("operation", "delete_all_details_permanent")
                .startSpan();

        logger.warn("🗑️ Permanently deleting all trashed merchant details");

        return merchantDetailCommandRepository.deleteAllDeleted()
                .map(deleted -> {
                    logger.info("Successfully permanently deleted all trashed merchant details");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_details_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All trashed merchant details permanently deleted", deleted);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete all trashed merchant details", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_details_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_details_permanent"));
                });
    }
}
