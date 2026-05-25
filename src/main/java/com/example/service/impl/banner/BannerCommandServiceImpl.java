package com.example.service.impl.banner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.RedisService;
import com.example.domain.requests.banner.CreateBannerRequest;
import com.example.domain.requests.banner.UpdateBannerRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.banner.BannerResponse;
import com.example.domain.response.banner.BannerResponseDeleteAt;
import com.example.entity.Banner;
import com.example.exception.ResourceAlreadyExistsException;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.banner.BannerCommandRepository;
import com.example.repository.banner.BannerQueryRepository;
import com.example.service.banner.BannerCommandService;

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
public class BannerCommandServiceImpl implements BannerCommandService {
        private static final Logger logger = LoggerFactory.getLogger(BannerCommandServiceImpl.class);

        BannerQueryRepository bannerQueryRepository;
        BannerCommandRepository bannerCommandRepository;
        OpenTelemetry openTelemetry;
        RedisService redisService;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        @Inject
        public BannerCommandServiceImpl(BannerQueryRepository bannerQueryRepository,
                        BannerCommandRepository bannerCommandRepository,
                        OpenTelemetry openTelemetry,
                        RedisService redisService) {
                this.bannerQueryRepository = bannerQueryRepository;
                this.bannerCommandRepository = bannerCommandRepository;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.tracer = openTelemetry.getTracer("banner-command-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("banner-command-service");

                this.requestsTotal = meter.counterBuilder("requests_total")
                                .setDescription("Total number of requests")
                                .build();
                this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                                .setDescription("Request duration in seconds")
                                .setUnit("s")
                                .build();
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<BannerResponse>> createBanner(CreateBannerRequest request) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("createBanner")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "banner-service")
                                .setAttribute("operation", "create_banner")
                                .setAttribute("banner.name", request.getName())
                                .startSpan();

                logger.info("Creating new banner with name: {}", request.getName());

                return bannerQueryRepository.findByName(request.getName())
                                .chain(existingBanner -> {
                                        if (existingBanner != null) {
                                                logger.warn("Banner creation failed - banner name '{}' already exists",
                                                                request.getName());
                                                span.setStatus(StatusCode.ERROR, "Banner already exists");
                                                span.setAttribute("banner.create.success", false);

                                                requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"), "create_banner",
                                                                AttributeKey.stringKey("status"), "failed",
                                                                AttributeKey.stringKey("error_type"),
                                                                "already_exists"));

                                                throw new ResourceAlreadyExistsException("Banner with name '"
                                                                + request.getName() + "' already exists");
                                        }

                                        Banner banner = new Banner();
                                        banner.setName(request.getName());
                                        banner.setStartDate(java.sql.Date
                                                        .valueOf(java.time.LocalDate.parse(request.getStartDate())));
                                        banner.setEndDate(java.sql.Date
                                                        .valueOf(java.time.LocalDate.parse(request.getEndDate())));
                                        banner.setStartTime(java.sql.Time
                                                        .valueOf(java.time.LocalTime.parse(request.getStartTime())));
                                        banner.setEndTime(java.sql.Time
                                                        .valueOf(java.time.LocalTime.parse(request.getEndTime())));
                                        banner.setIsActive(request.getIsActive());
                                        banner.setCreatedAt(java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
                                        banner.setUpdatedAt(java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));

                                        return bannerCommandRepository.persist(banner)
                                                        .map(v -> {
                                                                span.setAttribute("banner.id", banner.id);
                                                                span.setAttribute("banner.create.success", true);

                                                                BannerResponse bannerResponse = BannerResponse
                                                                                .from(banner);

                                                                logger.info("Successfully created banner with id: {} and name: {}",
                                                                                banner.id, banner.getName());
                                                                span.setStatus(StatusCode.OK);

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "create_banner",
                                                                                AttributeKey.stringKey("status"),
                                                                                "success"));

                                                                return ApiResponse.success(
                                                                                "Banner created successfully!",
                                                                                bannerResponse);
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error creating banner with name: {}", request.getName(), e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "create_banner",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "create_banner"));
                                        logger.debug("Create banner operation completed in {} seconds", duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<BannerResponse>> updateBanner(UpdateBannerRequest request) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("updateBanner")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "banner-service")
                                .setAttribute("operation", "update_banner")
                                .setAttribute("banner.id",
                                                request.getBannerID() != null ? request.getBannerID().toString()
                                                                : "null")
                                .startSpan();

                logger.info("Updating banner with id: {}", request.getBannerID());

                if (request.getBannerID() == null) {
                        span.setStatus(StatusCode.ERROR, "banner_id is required");
                        throw new ResourceNotFoundException("banner_id is required");
                }

                return bannerCommandRepository.findById(request.getBannerID().longValue())
                                .chain(existingBanner -> {
                                        if (existingBanner == null) {
                                                logger.warn("Banner update failed - banner not found with id: {}",
                                                                request.getBannerID());
                                                span.setStatus(StatusCode.ERROR, "Banner not found");
                                                span.setAttribute("banner.update.success", false);

                                                requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"), "update_banner",
                                                                AttributeKey.stringKey("status"), "failed",
                                                                AttributeKey.stringKey("error_type"), "not_found"));

                                                throw new ResourceNotFoundException("Banner not found");
                                        }

                                        existingBanner.setName(request.getName());
                                        existingBanner.setStartDate(java.sql.Date
                                                        .valueOf(java.time.LocalDate.parse(request.getStartDate())));
                                        existingBanner.setEndDate(java.sql.Date
                                                        .valueOf(java.time.LocalDate.parse(request.getEndDate())));
                                        existingBanner.setStartTime(java.sql.Time
                                                        .valueOf(java.time.LocalTime.parse(request.getStartTime())));
                                        existingBanner.setEndTime(java.sql.Time
                                                        .valueOf(java.time.LocalTime.parse(request.getEndTime())));
                                        existingBanner.setIsActive(request.getIsActive());
                                        existingBanner.setUpdatedAt(
                                                        java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));

                                        return bannerCommandRepository.persist(existingBanner)
                                                        .chain(v -> {
                                                                BannerResponse bannerResponse = BannerResponse
                                                                                .from(existingBanner);
                                                                String cacheKey = "banner:" + request.getBannerID();

                                                                return redisService.deleteReactive(cacheKey)
                                                                                .map(v2 -> {
                                                                                        logger.info("Invalidated cache for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully updated banner with id: {}",
                                                                                                        request.getBannerID());
                                                                                        span.setStatus(StatusCode.OK);
                                                                                        span.setAttribute(
                                                                                                        "banner.update.success",
                                                                                                        true);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "update_banner",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return ApiResponse.success(
                                                                                                        "Banner updated successfully!",
                                                                                                        bannerResponse);
                                                                                });
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error updating banner with id: {}", request.getBannerID(), e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "update_banner",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "update_banner"));
                                        logger.debug("Update banner operation completed in {} seconds", duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<BannerResponseDeleteAt>> trashedBanner(Long bannerId) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("trashBanner")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "banner-service")
                                .setAttribute("operation", "trash_banner")
                                .setAttribute("banner.id", bannerId.toString())
                                .startSpan();

                logger.info("Trashing banner with id: {}", bannerId);

                return bannerCommandRepository.trashed(bannerId)
                                .chain(trashedBanner -> {
                                        if (trashedBanner == null) {
                                                logger.warn("Banner trash failed - banner not found with id: {}",
                                                                bannerId);
                                                span.setStatus(StatusCode.ERROR, "Banner not found");
                                                span.setAttribute("banner.trash.success", false);

                                                requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"), "trash_banner",
                                                                AttributeKey.stringKey("status"), "failed",
                                                                AttributeKey.stringKey("error_type"), "not_found"));

                                                throw new ResourceNotFoundException(
                                                                "Trashed banner not found with id: " + bannerId);
                                        }

                                        span.setAttribute("banner.name", trashedBanner.getName());
                                        span.setAttribute("banner.trash.success", true);

                                        BannerResponseDeleteAt response = BannerResponseDeleteAt.from(trashedBanner);
                                        String cacheKey = "banner:" + bannerId;

                                        return redisService.deleteReactive(cacheKey)
                                                        .map(v -> {
                                                                logger.info("Invalidated cache for key: {}", cacheKey);
                                                                logger.info("Successfully trashed banner with id: {}",
                                                                                bannerId);
                                                                span.setStatus(StatusCode.OK);

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "trash_banner",
                                                                                AttributeKey.stringKey("status"),
                                                                                "success"));

                                                                return ApiResponse.success(
                                                                                "Banner trashed successfully!",
                                                                                response);
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error trashing banner with id: {}", bannerId, e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "trash_banner",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "trash_banner"));
                                        logger.debug("Trash banner operation completed in {} seconds", duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<BannerResponseDeleteAt>> restoreBanner(Long bannerId) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("restoreBanner")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "banner-service")
                                .setAttribute("operation", "restore_banner")
                                .setAttribute("banner.id", bannerId.toString())
                                .startSpan();

                logger.info("Restoring banner with id: {}", bannerId);

                return bannerCommandRepository.restore(bannerId)
                                .chain(restoredBanner -> {
                                        if (restoredBanner == null) {
                                                logger.warn("Banner restore failed - banner not found with id: {}",
                                                                bannerId);
                                                span.setStatus(StatusCode.ERROR, "Banner not found");
                                                span.setAttribute("banner.restore.success", false);

                                                requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"), "restore_banner",
                                                                AttributeKey.stringKey("status"), "failed",
                                                                AttributeKey.stringKey("error_type"), "not_found"));

                                                throw new ResourceNotFoundException(
                                                                "Restore banner not found with id: " + bannerId);
                                        }

                                        span.setAttribute("banner.name", restoredBanner.getName());
                                        span.setAttribute("banner.restore.success", true);

                                        BannerResponseDeleteAt response = BannerResponseDeleteAt.from(restoredBanner);
                                        String cacheKey = "banner:" + bannerId;

                                        return redisService.deleteReactive(cacheKey)
                                                        .map(v -> {
                                                                logger.info("Invalidated cache for key: {}", cacheKey);
                                                                logger.info("Successfully restored banner with id: {}",
                                                                                bannerId);
                                                                span.setStatus(StatusCode.OK);

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "restore_banner",
                                                                                AttributeKey.stringKey("status"),
                                                                                "success"));

                                                                return ApiResponse.success(
                                                                                "Banner restored successfully!",
                                                                                response);
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error restoring banner with id: {}", bannerId, e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_banner",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_banner"));
                                        logger.debug("Restore banner operation completed in {} seconds", duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Void>> deleteBannerPermanent(Long bannerId) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("deleteBannerPermanent")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "banner-service")
                                .setAttribute("operation", "delete_banner_permanent")
                                .setAttribute("banner.id", bannerId.toString())
                                .startSpan();

                logger.info("Permanently deleting banner with id: {}", bannerId);

                return bannerCommandRepository.findById(bannerId)
                                .chain(bannerToDelete -> {
                                        if (bannerToDelete == null) {
                                                logger.warn("Permanent delete failed - banner not found with id: {}",
                                                                bannerId);
                                                span.setStatus(StatusCode.ERROR, "Banner not found");
                                                span.setAttribute("banner.delete.success", false);

                                                requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"),
                                                                "delete_banner_permanent",
                                                                AttributeKey.stringKey("status"), "failed",
                                                                AttributeKey.stringKey("error_type"), "not_found"));

                                                throw new ResourceNotFoundException(
                                                                "Banner not found with id: " + bannerId);
                                        }

                                        span.setAttribute("banner.name", bannerToDelete.getName());

                                        return bannerCommandRepository.deletePermanent(bannerId)
                                                        .chain(v -> {
                                                                String cacheKey = "banner:" + bannerId;
                                                                return redisService.deleteReactive(cacheKey)
                                                                                .map(v2 -> {
                                                                                        logger.info("Invalidated cache for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully permanently deleted banner with id: {}",
                                                                                                        bannerId);
                                                                                        span.setStatus(StatusCode.OK);
                                                                                        span.setAttribute(
                                                                                                        "banner.delete.success",
                                                                                                        true);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "delete_banner_permanent",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return ApiResponse.success(
                                                                                                        "Banner deleted permanently!");
                                                                                });
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error permanently deleting banner with id: {}", bannerId, e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "delete_banner_permanent",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_banner_permanent"));
                                        logger.debug("Permanent delete banner operation completed in {} seconds",
                                                        duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Void>> restoreAllBanner() {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("restoreAllBanner")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "banner-service")
                                .setAttribute("operation", "restore_all_banners")
                                .startSpan();

                logger.info("Restoring all trashed banners");

                return bannerCommandRepository.restoreAllDeleted()
                                .map(v -> {
                                        logger.warn("All trashed banners restored. Caches will be refreshed upon expiry.");
                                        logger.info("Successfully restored all trashed banners");
                                        span.setStatus(StatusCode.OK);

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_all_banners",
                                                        AttributeKey.stringKey("status"), "success"));

                                        return ApiResponse.success("All banners restored successfully!");
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error restoring all banners", e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_all_banners",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_all_banners"));
                                        logger.debug("Restore all banners operation completed in {} seconds", duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Void>> deleteAllBannerPermanent() {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("deleteAllBannerPermanent")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "banner-service")
                                .setAttribute("operation", "delete_all_banners_permanent")
                                .startSpan();

                logger.info("Permanently deleting all trashed banners");

                return bannerCommandRepository.deleteAllDeleted()
                                .map(v -> {
                                        logger.warn("All trashed banners permanently deleted. Caches will be refreshed upon expiry.");
                                        logger.info("Successfully permanently deleted all trashed banners");
                                        span.setStatus(StatusCode.OK);

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_all_banners_permanent",
                                                        AttributeKey.stringKey("status"), "success"));

                                        return ApiResponse.success("All banners permanently deleted!");
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error permanently deleting all banners", e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_all_banners_permanent",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_all_banners_permanent"));
                                        logger.debug("Delete all banners permanent operation completed in {} seconds",
                                                        duration);
                                });
        }
}
