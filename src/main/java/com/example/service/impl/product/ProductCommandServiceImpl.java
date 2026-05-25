package com.example.service.impl.product;

import java.io.File;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.RedisService;
import com.example.domain.requests.FileUpload;
import com.example.domain.requests.product.CreateProductRequest;
import com.example.domain.requests.product.UpdateProductRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.product.ProductResponse;
import com.example.domain.response.product.ProductResponseDeleteAt;
import com.example.entity.Product;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.merchant.MerchantQueryRepository;
import com.example.repository.product.ProductCommandRepository;
import com.example.repository.product.ProductQueryRepository;
import com.example.service.FileService;
import com.example.service.FolderService;
import com.example.service.product.ProductCommandService;

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
public class ProductCommandServiceImpl implements ProductCommandService {
    private static final Logger logger = LoggerFactory.getLogger(ProductCommandServiceImpl.class);

    ProductCommandRepository productCommandRepository;
    ProductQueryRepository productQueryRepository;
    MerchantQueryRepository merchantQueryRepository;
    Validator validator;
    FileService fileService;
    FolderService folderService;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final String PRODUCT_BASE_PATH = "static/product";

    @Inject
    public ProductCommandServiceImpl(ProductCommandRepository productCommandRepository,
            ProductQueryRepository productQueryRepository,
            MerchantQueryRepository merchantQueryRepository,
            Validator validator,
            FileService fileService,
            FolderService folderService,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.productCommandRepository = productCommandRepository;
        this.productQueryRepository = productQueryRepository;
        this.merchantQueryRepository = merchantQueryRepository;
        this.validator = validator;
        this.fileService = fileService;
        this.folderService = folderService;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("product-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("product-command-service");

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

    private Uni<Void> invalidateCache(Long productId) {
        if (productId != null) {
            return redisService.deleteReactive("product:id:" + productId).replaceWithVoid();
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ProductResponse>> createProduct(CreateProductRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createProduct")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "create_product")
                .setAttribute("product.name", req.getName())
                .startSpan();

        logger.info("🆕 Creating product: {}", req.getName());

        try {
            validateRequest(req);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        return createFolderReactive(PRODUCT_BASE_PATH, req.getSlugProduct())
                .chain(folderPath -> {
                    if (folderPath == null) {
                        span.setStatus(StatusCode.ERROR, "Failed to create folder for product");
                        throw new RuntimeException("Failed to create folder for product");
                    }
                    String filePath = folderPath + File.separator + "product.jpg";
                    return createFileImageReactive(req.getImageProduct(), filePath);
                })
                .chain(savedPath -> {
                    if (savedPath == null) {
                        span.setStatus(StatusCode.ERROR, "Failed to save product image");
                        throw new RuntimeException("Failed to save product image");
                    }

                    Product product = new Product();
                    product.setMerchantId(req.getMerchantId());
                    product.setCategoryId(req.getCategoryId());
                    product.setName(req.getName());
                    product.setDescription(req.getDescription());
                    product.setPrice(req.getPrice());
                    product.setCountInStock(req.getCountInStock());
                    product.setBrand(req.getBrand());
                    product.setWeight(req.getWeight());
                    product.setRating(req.getRating().floatValue());
                    product.setSlugProduct(req.getSlugProduct());
                    product.setImageProduct(savedPath);

                    return productCommandRepository.persist(product);
                })
                .chain(saved -> {
                    ProductResponse response = ProductResponse.from(saved);
                    span.setAttribute("product.id", saved.id);

                    return invalidateCache(saved.id)
                            .map(v -> {
                                logger.info("Successfully created product with ID: {}", saved.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "create_product",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Product created successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to create product: {}", req.getName(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_product",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_product"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ProductResponse>> updateProduct(UpdateProductRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateProduct")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "update_product")
                .setAttribute("product.id", req.getProductId().toString())
                .startSpan();

        logger.info("✏️ Updating product ID: {}", req.getProductId());

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
                        throw new ResourceNotFoundException("Merchant not found with id " + req.getMerchantId());
                    }
                    return productQueryRepository.findProductById(req.getProductId().longValue());
                })
                .chain(optProduct -> {
                    if (optProduct.isEmpty()) {
                        throw new ResourceNotFoundException("Product not found");
                    }
                    Product product = optProduct.get();

                    Uni<Void> deleteOldImageUni = (req.getImageProduct() != null && product.getImageProduct() != null)
                            ? deleteFileImageReactive(product.getImageProduct())
                            : Uni.createFrom().voidItem();

                    return deleteOldImageUni.chain(() -> {
                        if (req.getImageProduct() != null) {
                            return createFolderReactive(PRODUCT_BASE_PATH, req.getSlugProduct())
                                    .chain(folderPath -> {
                                        if (folderPath == null) {
                                            throw new RuntimeException("Failed to create folder for product");
                                        }
                                        String filePath = folderPath + File.separator + "product.jpg";
                                        return createFileImageReactive(req.getImageProduct(), filePath);
                                    })
                                    .chain(savedPath -> {
                                        if (savedPath == null) {
                                            throw new RuntimeException("Failed to save product image");
                                        }
                                        product.setImageProduct(savedPath);
                                        return Uni.createFrom().item(product);
                                    });
                        } else {
                            return Uni.createFrom().item(product);
                        }
                    });
                })
                .chain(product -> {
                    product.setMerchantId(req.getMerchantId());
                    product.setCategoryId(req.getCategoryId());
                    product.setName(req.getName());
                    product.setDescription(req.getDescription());
                    product.setPrice(req.getPrice());
                    product.setCountInStock(req.getCountInStock());
                    product.setBrand(req.getBrand());
                    product.setWeight(req.getWeight());
                    product.setRating(req.getRating().floatValue());
                    product.setSlugProduct(req.getSlugProduct());

                    return productCommandRepository.persist(product);
                })
                .chain(updated -> {
                    ProductResponse response = ProductResponse.from(updated);

                    return invalidateCache(updated.id)
                            .map(v -> {
                                logger.info("Successfully updated product with ID: {}", updated.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "update_product",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Product updated successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to update product ID: {}", req.getProductId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_product",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_product"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ProductResponseDeleteAt>> trashedProduct(Integer productId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashProduct")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "trash_product")
                .setAttribute("product.id", productId.toString())
                .startSpan();

        logger.info("🗑️ Trashing product ID: {}", productId);

        return productCommandRepository.trashed(productId.longValue())
                .chain(product -> {
                    if (product == null) {
                        throw new ResourceNotFoundException("Product not found or already trashed");
                    }
                    ProductResponseDeleteAt response = ProductResponseDeleteAt.from(product);

                    return invalidateCache(productId.longValue())
                            .map(v -> {
                                logger.info("Successfully trashed product with ID: {}", productId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_product",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Product trashed successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to trash product ID: {}", productId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_product",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_product"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ProductResponseDeleteAt>> restoreProduct(Integer productId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreProduct")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "restore_product")
                .setAttribute("product.id", productId.toString())
                .startSpan();

        logger.info("♻️ Restoring product ID: {}", productId);

        return productCommandRepository.restore(productId.longValue())
                .chain(product -> {
                    if (product == null) {
                        throw new ResourceNotFoundException("Product not found or not trashed");
                    }
                    ProductResponseDeleteAt response = ProductResponseDeleteAt.from(product);

                    return invalidateCache(productId.longValue())
                            .map(v -> {
                                logger.info("Successfully restored product with ID: {}", productId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_product",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Product restored successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore product ID: {}", productId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_product",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_product"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteProductPermanent(Integer productId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteProductPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "delete_product_permanent")
                .setAttribute("product.id", productId.toString())
                .startSpan();

        logger.warn("🧨 Permanently deleting product ID: {}", productId);

        return productCommandRepository.deletePermanent(productId.longValue())
                .chain(deleted -> {
                    return invalidateCache(productId.longValue())
                            .map(v -> {
                                logger.info("Successfully permanently deleted product with ID: {}", productId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "delete_product_permanent",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Product permanently deleted", deleted);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete product ID: {}", productId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_product_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_product_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAllProducts() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllProducts")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "restore_all_products")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed products");

        return productCommandRepository.restoreAllDeleted()
                .map(restored -> {
                    logger.info("Successfully restored all trashed products");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_products",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All products restored successfully", restored);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore all products", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_products",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_products"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAllProductsPermanent() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllProductsPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "delete_all_products_permanent")
                .startSpan();

        logger.warn("💣 Permanently deleting ALL trashed products");

        return productCommandRepository.deleteAllDeleted()
                .map(deleted -> {
                    logger.info("Successfully permanently deleted all trashed products");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_products_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All products permanently deleted", deleted);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to delete all products", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_products_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_products_permanent"));
                });
    }
}
