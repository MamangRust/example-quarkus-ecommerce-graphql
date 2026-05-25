package com.example.service.impl.slider;

import java.io.File;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.RedisService;
import com.example.domain.requests.FileUpload;
import com.example.domain.requests.slider.CreateSliderRequest;
import com.example.domain.requests.slider.UpdateSliderRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.slider.SliderResponse;
import com.example.domain.response.slider.SliderResponseDeleteAt;
import com.example.entity.Slider;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.slider.SliderCommandRepository;
import com.example.repository.slider.SliderQueryRepository;
import com.example.service.FileService;
import com.example.service.FolderService;
import com.example.service.slider.SliderCommandService;

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
public class SliderCommandServiceImpl implements SliderCommandService {
    private static final Logger logger = LoggerFactory.getLogger(SliderCommandServiceImpl.class);

    SliderCommandRepository sliderCommandRepository;
    SliderQueryRepository sliderQueryRepository;
    Validator validator;
    FileService fileService;
    FolderService folderService;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final String SLIDER_BASE_PATH = "static/slider";

    @Inject
    public SliderCommandServiceImpl(SliderCommandRepository sliderCommandRepository,
            SliderQueryRepository sliderQueryRepository,
            Validator validator,
            FileService fileService,
            FolderService folderService,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.sliderCommandRepository = sliderCommandRepository;
        this.sliderQueryRepository = sliderQueryRepository;
        this.validator = validator;
        this.fileService = fileService;
        this.folderService = folderService;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("slider-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("slider-command-service");

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
            logger.warn("⚠️ Validation failed: {}", sb);
            throw new ConstraintViolationException("Validation failed: " + sb, violations);
        }
    }

    private Uni<Void> invalidateSliderCaches() {
        // Sliders lists change, invalidate standard listing caches
        return redisService.deleteReactive("slider:all:*")
                .chain(v -> redisService.deleteReactive("slider:active:*"))
                .chain(v -> redisService.deleteReactive("slider:trashed:*"))
                .replaceWithVoid()
                .onFailure().recoverWithItem((Void) null); // robust against cache deletion errors
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<SliderResponse>> createSlider(CreateSliderRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createSlider")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "slider-service")
                .setAttribute("operation", "create_slider")
                .setAttribute("slider.name", req.getNama())
                .startSpan();

        logger.info("🆕 Creating slider: {}", req.getNama());

        try {
            validateRequest(req);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        return createFolderReactive(SLIDER_BASE_PATH, req.getNama().replace(" ", "_"))
                .chain(folderPath -> {
                    if (folderPath == null) {
                        span.setStatus(StatusCode.ERROR, "Failed to create folder");
                        throw new RuntimeException("Failed to create folder for slider");
                    }
                    String filePath = folderPath + File.separator + "slider.jpg";
                    return createFileImageReactive(req.getFilePath(), filePath);
                })
                .chain(savedPath -> {
                    if (savedPath == null) {
                        span.setStatus(StatusCode.ERROR, "Failed to save image");
                        throw new RuntimeException("Failed to save slider image");
                    }

                    Slider slider = new Slider();
                    slider.setName(req.getNama());
                    slider.setImage(savedPath);

                    return sliderCommandRepository.persist(slider);
                })
                .chain(saved -> {
                    SliderResponse response = SliderResponse.from(saved);
                    span.setAttribute("slider.id", saved.id);

                    return invalidateSliderCaches()
                            .map(v -> {
                                logger.info("✅ Slider created successfully id={}", saved.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "create_slider",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Slider created successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to create slider", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_slider",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_slider"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<SliderResponse>> updateSlider(UpdateSliderRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateSlider")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "slider-service")
                .setAttribute("operation", "update_slider")
                .setAttribute("slider.id", req.getId() != null ? req.getId().toString() : "null")
                .startSpan();

        logger.info("✏️ Updating slider ID: {}", req.getId());

        try {
            validateRequest(req);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        return sliderCommandRepository.findById(req.getId().longValue())
                .chain(slider -> {
                    if (slider == null) {
                        span.setStatus(StatusCode.ERROR, "Slider not found");
                        throw new ResourceNotFoundException("Slider not found");
                    }

                    Uni<Void> deleteOldImageUni = (req.getFilePath() != null && slider.getImage() != null)
                            ? deleteFileImageReactive(slider.getImage())
                            : Uni.createFrom().voidItem();

                    return deleteOldImageUni.chain(() -> {
                        if (req.getFilePath() != null) {
                            return createFolderReactive(SLIDER_BASE_PATH, req.getNama().replace(" ", "_"))
                                    .chain(folderPath -> {
                                        if (folderPath == null) {
                                            logger.error("Failed to create folder for slider");
                                            throw new RuntimeException("Failed to create folder for slider");
                                        }
                                        String filePath = folderPath + File.separator + "slider.jpg";
                                        return createFileImageReactive(req.getFilePath(), filePath);
                                    })
                                    .chain(savedPath -> {
                                        if (savedPath == null) {
                                            logger.error("Failed to save slider image");
                                            throw new RuntimeException("Failed to save slider image");
                                        }
                                        slider.setImage(savedPath);
                                        slider.setName(req.getNama());
                                        return sliderCommandRepository.persist(slider);
                                    });
                        } else {
                            slider.setName(req.getNama());
                            return sliderCommandRepository.persist(slider);
                        }
                    });
                })
                .chain(updated -> {
                    SliderResponse response = SliderResponse.from(updated);

                    return invalidateSliderCaches()
                            .map(v -> {
                                logger.info("✅ Slider updated successfully id={}", updated.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "update_slider",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Slider updated successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to update slider ID: {}", req.getId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_slider",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_slider"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<SliderResponseDeleteAt>> trashedSlider(Integer sliderId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashedSlider")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "slider-service")
                .setAttribute("operation", "trash_slider")
                .setAttribute("slider.id", sliderId.toString())
                .startSpan();

        logger.info("🗑️ Trashing slider id={}", sliderId);

        return sliderCommandRepository.trashed(sliderId.longValue())
                .chain(slider -> {
                    if (slider == null) {
                        throw new ResourceNotFoundException("Slider not found or already trashed");
                    }
                    SliderResponseDeleteAt response = SliderResponseDeleteAt.from(slider);

                    return invalidateSliderCaches()
                            .map(v -> {
                                logger.info("Successfully trashed slider with ID: {}", sliderId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_slider",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("🗑️ Slider trashed successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to trash slider id={}", sliderId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_slider",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_slider"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<SliderResponseDeleteAt>> restoreSlider(Integer sliderId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreSlider")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "slider-service")
                .setAttribute("operation", "restore_slider")
                .setAttribute("slider.id", sliderId.toString())
                .startSpan();

        logger.info("♻️ Restoring slider id={}", sliderId);

        return sliderCommandRepository.restore(sliderId.longValue())
                .chain(slider -> {
                    if (slider == null) {
                        throw new ResourceNotFoundException("Slider not found or not trashed");
                    }
                    SliderResponseDeleteAt response = SliderResponseDeleteAt.from(slider);

                    return invalidateSliderCaches()
                            .map(v -> {
                                logger.info("Successfully restored slider with ID: {}", sliderId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_slider",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("♻️ Slider restored successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore slider id={}", sliderId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_slider",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_slider"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteSliderPermanent(Integer sliderId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteSliderPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "slider-service")
                .setAttribute("operation", "delete_slider_permanent")
                .setAttribute("slider.id", sliderId.toString())
                .startSpan();

        logger.warn("🧨 Permanently deleting slider id={}", sliderId);

        return sliderCommandRepository.findById(sliderId.longValue())
                .chain(existing -> {
                    if (existing == null) {
                        throw new ResourceNotFoundException("Slider not found");
                    }

                    Uni<Void> deleteFileUni = (existing.getImage() != null)
                            ? deleteFileImageReactive(existing.getImage())
                            : Uni.createFrom().voidItem();

                    return deleteFileUni.chain(() -> sliderCommandRepository.deletePermanent(sliderId.longValue()));
                })
                .chain(deleted -> {
                    return invalidateSliderCaches()
                            .map(v -> {
                                logger.info("Successfully permanently deleted slider with ID: {}", sliderId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "delete_slider_permanent",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("🧨 Slider permanently deleted!", deleted);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to permanently delete slider id={}", sliderId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_slider_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_slider_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAllSliders() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllSliders")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "slider-service")
                .setAttribute("operation", "restore_all_sliders")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed sliders");

        return sliderCommandRepository.restoreAllDeleted()
                .chain(restored -> {
                    return invalidateSliderCaches()
                            .map(v -> {
                                logger.info("Successfully restored all trashed sliders");
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_all_sliders",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("🔄 All sliders restored successfully!", restored);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore all sliders", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_sliders",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_sliders"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAllSlidersPermanent() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllSlidersPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "slider-service")
                .setAttribute("operation", "delete_all_sliders_permanent")
                .startSpan();

        logger.warn("💣 Permanently deleting ALL trashed sliders");

        return sliderCommandRepository.deleteAllDeleted()
                .chain(deleted -> {
                    return invalidateSliderCaches()
                            .map(v -> {
                                logger.info("Successfully permanently deleted all trashed sliders");
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "delete_all_sliders_permanent",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("💣 All sliders permanently deleted!", deleted);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to delete all sliders", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_sliders_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_sliders_permanent"));
                });
    }
}
