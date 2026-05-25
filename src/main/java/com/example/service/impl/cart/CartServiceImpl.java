package com.example.service.impl.cart;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.config.RedisService;
import com.example.domain.requests.cart.CreateCartRequest;
import com.example.domain.requests.cart.DeleteCartRequest;
import com.example.domain.requests.cart.FindAllCartsRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.api.PagedResult;
import com.example.domain.response.api.PaginationMeta;
import com.example.domain.response.cart.CartResponse;
import com.example.entity.Cart;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.cart.CartCommandRepository;
import com.example.repository.cart.CartQueryRepository;
import com.example.service.CartService;

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
public class CartServiceImpl implements CartService {
        private static final Logger logger = LoggerFactory.getLogger(CartServiceImpl.class);

        CartQueryRepository cartQueryRepository;
        CartCommandRepository cartCommandRepository;
        OpenTelemetry openTelemetry;
        RedisService redisService;
        ObjectMapper objectMapper;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        private static final long LIST_CACHE_TTL_SECONDS = 300;

        @Inject
        public CartServiceImpl(CartQueryRepository cartQueryRepository,
                        CartCommandRepository cartCommandRepository,
                        OpenTelemetry openTelemetry,
                        RedisService redisService,
                        ObjectMapper objectMapper) {
                this.cartQueryRepository = cartQueryRepository;
                this.cartCommandRepository = cartCommandRepository;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.objectMapper = objectMapper;
                this.tracer = openTelemetry.getTracer("cart-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("cart-service");

                this.requestsTotal = meter.counterBuilder("requests_total")
                                .setDescription("Total number of requests")
                                .build();
                this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                                .setDescription("Request duration in seconds")
                                .setUnit("s")
                                .build();
        }

        private String toJson(Object obj) {
                try {
                        return objectMapper.writeValueAsString(obj);
                } catch (JsonProcessingException e) {
                        logger.error("Error serializing object to JSON", e);
                        throw new RuntimeException("Failed to serialize object", e);
                }
        }

        private <T> T fromJson(String json, TypeReference<T> typeReference) {
                try {
                        return objectMapper.readValue(json, typeReference);
                } catch (JsonProcessingException e) {
                        logger.error("Error deserializing JSON to object with TypeReference", e);
                        throw new RuntimeException("Failed to deserialize JSON", e);
                }
        }

        @Override
        public Uni<ApiResponsePagination<List<CartResponse>>> findAll(FindAllCartsRequest req) {
                String cacheKey = String.format("carts:user:%d:%d:%d:%s", req.getUserId(), req.getPage(),
                                req.getPageSize(),
                                req.getSearch() != null ? req.getSearch() : "");

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<CartResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<CartResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findAllCarts")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "cart-service")
                                                        .setAttribute("operation", "find_all_carts")
                                                        .setAttribute("cart.userId", req.getUserId().toString())
                                                        .startSpan();

                                        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                                        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                                        String search = (req.getSearch() != null && !req.getSearch().isEmpty())
                                                        ? req.getSearch()
                                                        : "";

                                        return cartQueryRepository.findCartsByUser(req.getUserId(), search, page, size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("cart.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("cart.page", req.getPage());
                                                                span.setAttribute("cart.size", req.getPageSize());

                                                                ApiResponsePagination<List<CartResponse>> response = buildPaginatedResponse(
                                                                                pagedResult, req,
                                                                                "Cart data fetched successfully",
                                                                                CartResponse::from);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                LIST_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully retrieved {} carts",
                                                                                                        pagedResult.getTotalRecords());
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_all_carts",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding all carts for userId: {}",
                                                                                req.getUserId(), e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_all_carts",
                                                                                AttributeKey.stringKey("status"),
                                                                                "failed",
                                                                                AttributeKey.stringKey("error_type"),
                                                                                e.getClass().getSimpleName()));
                                                        })
                                                        .eventually(() -> {
                                                                span.end();
                                                                double duration = (System.currentTimeMillis()
                                                                                - startTime) / 1000.0;
                                                                requestDurationSeconds.record(duration, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_all_carts"));
                                                                logger.debug("Find all carts operation completed in {} seconds",
                                                                                duration);
                                                        });
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<CartResponse>> createCart(CreateCartRequest request) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("createCart")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "cart-service")
                                .setAttribute("operation", "create_cart")
                                .setAttribute("cart.userId",
                                                request.getUserId() != null ? request.getUserId().toString() : "null")
                                .setAttribute("cart.productId",
                                                request.getProductId() != null ? request.getProductId().toString()
                                                                : "null")
                                .startSpan();

                logger.info("Creating new cart for userId={} | productId={} | quantity={}",
                                request.getUserId(), request.getProductId(), request.getQuantity());

                Cart cart = new Cart();
                cart.setUserId(request.getUserId());
                cart.setProductId(request.getProductId());
                cart.setQuantity(request.getQuantity());
                cart.setName("Product " + request.getProductId());
                cart.setPrice(100);
                cart.setImage("default.png");
                cart.setWeight(100);
                cart.setCreatedAt(java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));

                return cartCommandRepository.persist(cart)
                                .map(v -> {
                                        span.setAttribute("cart.id", cart.id);
                                        span.setAttribute("cart.create.success", true);

                                        CartResponse cartResponse = CartResponse.from(cart);

                                        logger.info("Successfully created cart with id: {} for userId: {}", cart.id,
                                                        cart.getUserId());
                                        span.setStatus(StatusCode.OK);

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "create_cart",
                                                        AttributeKey.stringKey("status"), "success"));

                                        return ApiResponse.success("Cart created successfully", cartResponse);
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error creating cart for userId: {}", request.getUserId(), e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "create_cart",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "create_cart"));
                                        logger.debug("Create cart operation completed in {} seconds", duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Void>> deletePermanent(Long cartId) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("deleteCartPermanent")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "cart-service")
                                .setAttribute("operation", "delete_cart_permanent")
                                .setAttribute("cart.id", cartId.toString())
                                .startSpan();

                logger.info("Permanently deleting cart with id: {}", cartId);

                return cartCommandRepository.findById(cartId)
                                .chain(cartToDelete -> {
                                        if (cartToDelete == null) {
                                                logger.warn("Permanent delete failed - cart not found with id: {}",
                                                                cartId);
                                                span.setStatus(StatusCode.ERROR, "Cart not found");
                                                span.setAttribute("cart.delete.success", false);

                                                requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"),
                                                                "delete_cart_permanent",
                                                                AttributeKey.stringKey("status"), "failed",
                                                                AttributeKey.stringKey("error_type"), "not_found"));

                                                throw new ResourceNotFoundException(
                                                                "Cart not found with id: " + cartId);
                                        }

                                        return cartCommandRepository.deleteCartById(cartId)
                                                        .map(v -> {
                                                                logger.info("Successfully permanently deleted cart with id: {}",
                                                                                cartId);
                                                                span.setStatus(StatusCode.OK);
                                                                span.setAttribute("cart.delete.success", true);

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "delete_cart_permanent",
                                                                                AttributeKey.stringKey("status"),
                                                                                "success"));

                                                                return ApiResponse.success("Cart deleted permanently");
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error permanently deleting cart with id: {}", cartId, e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "delete_cart_permanent",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "delete_cart_permanent"));
                                        logger.debug("Permanent delete cart operation completed in {} seconds",
                                                        duration);
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Void>> deleteAllPermanently(DeleteCartRequest req) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("deleteCartsPermanently")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "cart-service")
                                .setAttribute("operation", "delete_carts_permanently")
                                .startSpan();

                logger.info("Permanently deleting carts with ids: {}", req.getCartIds());

                List<Long> ids = req.getCartIds().stream()
                                .map(Integer::longValue)
                                .toList();

                return cartCommandRepository.deleteCartsByIds(ids)
                                .map(v -> {
                                        logger.info("Successfully permanently deleted carts with ids: {}", ids);
                                        span.setStatus(StatusCode.OK);

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "delete_carts_permanently",
                                                        AttributeKey.stringKey("status"), "success"));

                                        return ApiResponse.success("Carts deleted permanently");
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("Error permanently deleting carts with ids: {}", req.getCartIds(),
                                                        e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "delete_carts_permanently",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_carts_permanently"));
                                        logger.debug("Permanent delete carts operation completed in {} seconds",
                                                        duration);
                                });
        }

        private <T, R> ApiResponsePagination<List<R>> buildPaginatedResponse(
                        PagedResult<T> pagedResult,
                        FindAllCartsRequest request,
                        String successMessage,
                        Function<T, R> mapper) {

                List<R> data = pagedResult.getData().stream()
                                .map(mapper)
                                .collect(Collectors.toList());

                int totalRecords = pagedResult.getTotalRecords();
                int size = request.getPageSize() > 0 ? request.getPageSize() : 1;
                int totalPages = (int) Math.ceil((double) totalRecords / size);

                PaginationMeta pagination = new PaginationMeta(request.getPage(), size, totalPages, totalRecords);

                return new ApiResponsePagination<>("success", successMessage, data, pagination);
        }
}
