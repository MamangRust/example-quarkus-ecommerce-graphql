package com.example.service.impl.category;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.RedisService;
import com.example.domain.requests.category.CreateCategoryRequest;
import com.example.domain.requests.category.UpdateCategoryRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.category.CategoryResponse;
import com.example.domain.response.category.CategoryResponseDeleteAt;
import com.example.entity.category.Category;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.category.CategoryCommandRepository;
import com.example.repository.category.CategoryQueryRepository;
import com.example.service.FileService;
import com.example.service.FolderService;
import com.example.service.category.CategoryCommandService;

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
public class CategoryCommandServiceImpl implements CategoryCommandService {
        private static final Logger logger = LoggerFactory.getLogger(CategoryCommandServiceImpl.class);

        CategoryCommandRepository categoryCommandRepository;
        CategoryQueryRepository categoryQueryRepository;
        FileService fileService;
        FolderService folderService;
        OpenTelemetry openTelemetry;
        RedisService redisService;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        private static final String CATEGORY_BASE_PATH = "static/category";

        @Inject
        public CategoryCommandServiceImpl(CategoryCommandRepository categoryCommandRepository,
                        CategoryQueryRepository categoryQueryRepository,
                        FileService fileService,
                        FolderService folderService,
                        OpenTelemetry openTelemetry,
                        RedisService redisService) {
                this.categoryCommandRepository = categoryCommandRepository;
                this.categoryQueryRepository = categoryQueryRepository;
                this.fileService = fileService;
                this.folderService = folderService;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.tracer = openTelemetry.getTracer("category-command-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("category-command-service");

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
        public Uni<ApiResponse<CategoryResponse>> createCategory(CreateCategoryRequest request) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("createCategory")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "category-service")
                                .setAttribute("operation", "create_category")
                                .setAttribute("category.name", request.getName())
                                .startSpan();

                logger.info("Creating new category with name: {}", request.getName());

                return Uni.createFrom().item(() -> {
                        String folderPath = folderService.createFolder(CATEGORY_BASE_PATH, request.getSlugCategory());
                        if (folderPath == null) {
                                logger.error("Failed to create folder for category: {}", request.getSlugCategory());
                                span.setStatus(StatusCode.ERROR, "Failed to create folder for category");
                                throw new RuntimeException("Failed to create folder for category");
                        }

                        String filePath = folderPath + java.io.File.separator + "category.jpg";
                        String savedPath = fileService.createFileImage(request.getImageCategory(), filePath);
                        if (savedPath == null) {
                                logger.error("Failed to save category image for: {}", request.getName());
                                span.setStatus(StatusCode.ERROR, "Failed to save category image");
                                throw new RuntimeException("Failed to save category image");
                        }
                        return savedPath;
                }).chain(savedPath -> {
                        Category category = new Category();
                        category.setName(request.getName());
                        category.setDescription(request.getDescription());
                        category.setSlugCategory(request.getSlugCategory());
                        category.setImageCategory(savedPath);
                        category.setCreatedAt(java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
                        category.setUpdatedAt(java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));

                        return categoryCommandRepository.persist(category)
                                        .map(v -> {
                                                span.setAttribute("category.id", category.id);
                                                span.setAttribute("category.create.success", true);

                                                CategoryResponse categoryResponse = CategoryResponse.from(category);

                                                logger.info("Successfully created category with id: {} and name: {}",
                                                                category.id, category.getName());
                                                span.setStatus(StatusCode.OK);

                                                requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"), "create_category",
                                                                AttributeKey.stringKey("status"), "success"));

                                                return ApiResponse.success("Category created successfully",
                                                                categoryResponse);
                                        });
                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error creating category: {}", request.getName(), e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "create_category",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "create_category"));
                                        logger.debug("Create category operation completed in {} seconds", duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<CategoryResponse>> updateCategory(UpdateCategoryRequest request) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("updateCategory")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "category-service")
                                .setAttribute("operation", "update_category")
                                .setAttribute("category.id",
                                                request.getCategoryId() != null ? request.getCategoryId().toString()
                                                                : "null")
                                .startSpan();

                logger.info("Updating category with id: {}", request.getCategoryId());

                if (request.getCategoryId() == null) {
                        span.setStatus(StatusCode.ERROR, "category_id is required");
                        throw new ResourceNotFoundException("category_id is required");
                }

                return categoryQueryRepository.findById(request.getCategoryId().longValue())
                                .chain(existingCategory -> {
                                        if (existingCategory == null) {
                                                logger.warn("Category update failed - category not found with id: {}",
                                                                request.getCategoryId());
                                                span.setStatus(StatusCode.ERROR, "Category not found");
                                                span.setAttribute("category.update.success", false);

                                                requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"), "update_category",
                                                                AttributeKey.stringKey("status"), "failed",
                                                                AttributeKey.stringKey("error_type"), "not_found"));

                                                throw new ResourceNotFoundException("Category not found");
                                        }

                                        if (existingCategory.getImageCategory() != null) {
                                                fileService.deleteFileImage(existingCategory.getImageCategory());
                                        }

                                        String folderPath = folderService.createFolder(CATEGORY_BASE_PATH,
                                                        request.getSlugCategory());
                                        String filePath = folderPath + java.io.File.separator
                                                        + "category.jpg";
                                        String savedPath = fileService.createFileImage(request.getImageCategory(),
                                                        filePath);

                                        existingCategory.setName(request.getName());
                                        existingCategory.setDescription(request.getDescription());
                                        existingCategory.setSlugCategory(request.getSlugCategory());
                                        existingCategory.setImageCategory(savedPath);
                                        existingCategory.setUpdatedAt(
                                                        java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));

                                        return categoryCommandRepository.persist(existingCategory)
                                                        .chain(v -> {
                                                                CategoryResponse categoryResponse = CategoryResponse
                                                                                .from(existingCategory);
                                                                String cacheKey = "categories:id:"
                                                                                + request.getCategoryId();

                                                                return redisService.deleteReactive(cacheKey)
                                                                                .map(v2 -> {
                                                                                        logger.info("Invalidated cache for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully updated category with id: {}",
                                                                                                        request.getCategoryId());
                                                                                        span.setStatus(StatusCode.OK);
                                                                                        span.setAttribute(
                                                                                                        "category.update.success",
                                                                                                        true);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "update_category",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return ApiResponse.success(
                                                                                                        "Category updated successfully",
                                                                                                        categoryResponse);
                                                                                });
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error updating category with id: {}", request.getCategoryId(), e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "update_category",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "update_category"));
                                        logger.debug("Update category operation completed in {} seconds", duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<CategoryResponseDeleteAt>> trashedCategory(Long categoryId) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("trashCategory")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "category-service")
                                .setAttribute("operation", "trash_category")
                                .setAttribute("category.id", categoryId.toString())
                                .startSpan();

                logger.info("Trashing category with id: {}", categoryId);

                return categoryCommandRepository.trashed(categoryId)
                                .chain(trashedCategory -> {
                                        if (trashedCategory == null) {
                                                logger.warn("Category trash failed - category not found with id: {}",
                                                                categoryId);
                                                span.setStatus(StatusCode.ERROR, "Category not found");
                                                span.setAttribute("category.trash.success", false);

                                                requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"), "trash_category",
                                                                AttributeKey.stringKey("status"), "failed",
                                                                AttributeKey.stringKey("error_type"), "not_found"));

                                                throw new ResourceNotFoundException(
                                                                "Trashed category not found with id: " + categoryId);
                                        }

                                        span.setAttribute("category.name", trashedCategory.getName());
                                        span.setAttribute("category.trash.success", true);

                                        CategoryResponseDeleteAt response = CategoryResponseDeleteAt
                                                        .from(trashedCategory);
                                        String cacheKey = "categories:id:" + categoryId;

                                        return redisService.deleteReactive(cacheKey)
                                                        .map(v -> {
                                                                logger.info("Invalidated cache for key: {}", cacheKey);
                                                                logger.info("Successfully trashed category with id: {}",
                                                                                categoryId);
                                                                span.setStatus(StatusCode.OK);

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "trash_category",
                                                                                AttributeKey.stringKey("status"),
                                                                                "success"));

                                                                return ApiResponse.success(
                                                                                "Category trashed successfully",
                                                                                response);
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error trashing category with id: {}", categoryId, e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "trash_category",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "trash_category"));
                                        logger.debug("Trash category operation completed in {} seconds", duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<CategoryResponseDeleteAt>> restoreCategory(Long categoryId) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("restoreCategory")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "category-service")
                                .setAttribute("operation", "restore_category")
                                .setAttribute("category.id", categoryId.toString())
                                .startSpan();

                logger.info("Restoring category with id: {}", categoryId);

                return categoryCommandRepository.restore(categoryId)
                                .chain(restoredCategory -> {
                                        if (restoredCategory == null) {
                                                logger.warn("Category restore failed - category not found with id: {}",
                                                                categoryId);
                                                span.setStatus(StatusCode.ERROR, "Category not found");
                                                span.setAttribute("category.restore.success", false);

                                                requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"), "restore_category",
                                                                AttributeKey.stringKey("status"), "failed",
                                                                AttributeKey.stringKey("error_type"), "not_found"));

                                                throw new ResourceNotFoundException(
                                                                "Restore category not found with id: " + categoryId);
                                        }

                                        span.setAttribute("category.name", restoredCategory.getName());
                                        span.setAttribute("category.restore.success", true);

                                        CategoryResponseDeleteAt response = CategoryResponseDeleteAt
                                                        .from(restoredCategory);
                                        String cacheKey = "categories:id:" + categoryId;

                                        return redisService.deleteReactive(cacheKey)
                                                        .map(v -> {
                                                                logger.info("Invalidated cache for key: {}", cacheKey);
                                                                logger.info("Successfully restored category with id: {}",
                                                                                categoryId);
                                                                span.setStatus(StatusCode.OK);

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "restore_category",
                                                                                AttributeKey.stringKey("status"),
                                                                                "success"));

                                                                return ApiResponse.success(
                                                                                "Category restored successfully",
                                                                                response);
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error restoring category with id: {}", categoryId, e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_category",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_category"));
                                        logger.debug("Restore category operation completed in {} seconds", duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Void>> deleteCategoryPermanent(Long categoryId) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("deleteCategoryPermanent")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "category-service")
                                .setAttribute("operation", "delete_category_permanent")
                                .setAttribute("category.id", categoryId.toString())
                                .startSpan();

                logger.info("Permanently deleting category with id: {}", categoryId);

                return categoryQueryRepository.findById(categoryId)
                                .chain(categoryToDelete -> {
                                        if (categoryToDelete == null) {
                                                logger.warn("Permanent delete failed - category not found with id: {}",
                                                                categoryId);
                                                span.setStatus(StatusCode.ERROR, "Category not found");
                                                span.setAttribute("category.delete.success", false);

                                                requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"),
                                                                "delete_category_permanent",
                                                                AttributeKey.stringKey("status"), "failed",
                                                                AttributeKey.stringKey("error_type"), "not_found"));

                                                throw new ResourceNotFoundException(
                                                                "Category not found with id: " + categoryId);
                                        }

                                        span.setAttribute("category.name", categoryToDelete.getName());

                                        return categoryCommandRepository.deletePermanent(categoryId)
                                                        .chain(v -> {
                                                                String cacheKey = "categories:id:" + categoryId;
                                                                return redisService.deleteReactive(cacheKey)
                                                                                .map(v2 -> {
                                                                                        logger.info("Invalidated cache for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully permanently deleted category with id: {}",
                                                                                                        categoryId);
                                                                                        span.setStatus(StatusCode.OK);
                                                                                        span.setAttribute(
                                                                                                        "category.delete.success",
                                                                                                        true);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "delete_category_permanent",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return ApiResponse.success(
                                                                                                        "Category deleted permanently");
                                                                                });
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error permanently deleting category with id: {}", categoryId, e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_category_permanent",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_category_permanent"));
                                        logger.debug("Permanent delete category operation completed in {} seconds",
                                                        duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Void>> restoreAllCategories() {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("restoreAllCategories")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "category-service")
                                .setAttribute("operation", "restore_all_categories")
                                .startSpan();

                logger.info("Restoring all trashed categories");

                return categoryCommandRepository.restoreAllDeleted()
                                .map(v -> {
                                        logger.warn("All trashed categories restored. Caches will be refreshed upon expiry.");
                                        logger.info("Successfully restored all trashed categories");
                                        span.setStatus(StatusCode.OK);

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_all_categories",
                                                        AttributeKey.stringKey("status"), "success"));

                                        return ApiResponse.success("All categories restored successfully");
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error restoring all categories", e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_all_categories",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_all_categories"));
                                        logger.debug("Restore all categories operation completed in {} seconds",
                                                        duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Void>> deleteAllCategoriesPermanent() {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("deleteAllCategoriesPermanent")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "category-service")
                                .setAttribute("operation", "delete_all_categories_permanent")
                                .startSpan();

                logger.info("Permanently deleting all trashed categories");

                return categoryCommandRepository.deleteAllDeleted()
                                .map(v -> {
                                        logger.warn("All trashed categories permanently deleted. Caches will be refreshed upon expiry.");
                                        logger.info("Successfully permanently deleted all trashed categories");
                                        span.setStatus(StatusCode.OK);

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_all_categories_permanent",
                                                        AttributeKey.stringKey("status"), "success"));

                                        return ApiResponse.success("All categories permanently deleted");
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error permanently deleting all categories", e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_all_categories_permanent",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_all_categories_permanent"));
                                        logger.debug("Delete all categories permanent operation completed in {} seconds",
                                                        duration);
                                });
        }
}
