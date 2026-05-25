package com.example.service.impl.shippingaddress;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.config.RedisService;
import com.example.domain.requests.shipping.FindAllShippingAddress;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.api.PagedResult;
import com.example.domain.response.api.PaginationMeta;
import com.example.domain.response.shipping.ShippingAddressResponse;
import com.example.domain.response.shipping.ShippingAddressResponseDeleteAt;
import com.example.entity.ShippingAddress;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.shippingaddress.ShippingAddressQueryRepository;
import com.example.service.shippingaddress.ShippingAddressQueryService;

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
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ShippingAddressQueryServiceImpl implements ShippingAddressQueryService {
        private static final Logger logger = LoggerFactory.getLogger(ShippingAddressQueryServiceImpl.class);

        ShippingAddressQueryRepository shippingAddressQueryRepository;
        OpenTelemetry openTelemetry;
        RedisService redisService;
        ObjectMapper objectMapper;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        private static final long LIST_CACHE_TTL_SECONDS = 300;

        @Inject
        public ShippingAddressQueryServiceImpl(ShippingAddressQueryRepository shippingAddressQueryRepository,
                        OpenTelemetry openTelemetry,
                        RedisService redisService,
                        ObjectMapper objectMapper) {
                this.shippingAddressQueryRepository = shippingAddressQueryRepository;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.objectMapper = objectMapper;
                this.tracer = openTelemetry.getTracer("shipping-address-query-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("shipping-address-query-service");

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

        private <T> T fromJson(String json, Class<T> clazz) {
                try {
                        return objectMapper.readValue(json, clazz);
                } catch (JsonProcessingException e) {
                        logger.error("Error deserializing JSON to object", e);
                        throw new RuntimeException("Failed to deserialize JSON", e);
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
        public Uni<ApiResponsePagination<List<ShippingAddressResponse>>> findAll(FindAllShippingAddress req) {
                String cacheKey = String.format("shipping:all:%d:%d:%s", req.getPage(), req.getPageSize(),
                                req.getSearch() != null ? req.getSearch() : "");

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<ShippingAddressResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<ShippingAddressResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findAllShippingAddresses")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "shipping-address-service")
                                                        .setAttribute("operation", "find_all_shipping_addresses")
                                                        .startSpan();

                                        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                                        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                                        String search = (req.getSearch() != null && !req.getSearch().isEmpty())
                                                        ? req.getSearch()
                                                        : "";

                                        return shippingAddressQueryRepository.findShippingAddresses(search, page, size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("address.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("address.page", req.getPage());
                                                                span.setAttribute("address.size", req.getPageSize());

                                                                ApiResponsePagination<List<ShippingAddressResponse>> response = buildPaginatedResponse(
                                                                                pagedResult, req.getPage(),
                                                                                req.getPageSize(),
                                                                                "Shipping addresses retrieved successfully",
                                                                                ShippingAddressResponse::from);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                LIST_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_all_shipping_addresses",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding all shipping addresses", e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_all_shipping_addresses",
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
                                                                                "find_all_shipping_addresses"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponsePagination<List<ShippingAddressResponseDeleteAt>>> findByActive(
                        FindAllShippingAddress req) {
                String cacheKey = String.format("shipping:active:%d:%d:%s", req.getPage(), req.getPageSize(),
                                req.getSearch() != null ? req.getSearch() : "");

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<ShippingAddressResponseDeleteAt>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<ShippingAddressResponseDeleteAt>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findActiveShippingAddresses")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "shipping-address-service")
                                                        .setAttribute("operation", "find_active_shipping_addresses")
                                                        .startSpan();

                                        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                                        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                                        String search = (req.getSearch() != null && !req.getSearch().isEmpty())
                                                        ? req.getSearch()
                                                        : "";

                                        return shippingAddressQueryRepository
                                                        .findActiveShippingAddresses(search, page, size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("address.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("address.page", req.getPage());
                                                                span.setAttribute("address.size", req.getPageSize());

                                                                ApiResponsePagination<List<ShippingAddressResponseDeleteAt>> response = buildPaginatedResponse(
                                                                                pagedResult, req.getPage(),
                                                                                req.getPageSize(),
                                                                                "Active shipping addresses retrieved successfully",
                                                                                ShippingAddressResponseDeleteAt::from);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                LIST_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_active_shipping_addresses",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding active shipping addresses",
                                                                                e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_active_shipping_addresses",
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
                                                                                "find_active_shipping_addresses"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponsePagination<List<ShippingAddressResponseDeleteAt>>> findByTrashed(
                        FindAllShippingAddress req) {
                String cacheKey = String.format("shipping:trashed:%d:%d:%s", req.getPage(), req.getPageSize(),
                                req.getSearch() != null ? req.getSearch() : "");

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<ShippingAddressResponseDeleteAt>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<ShippingAddressResponseDeleteAt>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findTrashedShippingAddresses")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "shipping-address-service")
                                                        .setAttribute("operation", "find_trashed_shipping_addresses")
                                                        .startSpan();

                                        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                                        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                                        String search = (req.getSearch() != null && !req.getSearch().isEmpty())
                                                        ? req.getSearch()
                                                        : "";

                                        return shippingAddressQueryRepository
                                                        .findTrashedShippingAddresses(search, page, size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("address.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("address.page", req.getPage());
                                                                span.setAttribute("address.size", req.getPageSize());

                                                                ApiResponsePagination<List<ShippingAddressResponseDeleteAt>> response = buildPaginatedResponse(
                                                                                pagedResult, req.getPage(),
                                                                                req.getPageSize(),
                                                                                "Trashed shipping addresses retrieved successfully",
                                                                                ShippingAddressResponseDeleteAt::from);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                LIST_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_trashed_shipping_addresses",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding trashed shipping addresses",
                                                                                e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_trashed_shipping_addresses",
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
                                                                                "find_trashed_shipping_addresses"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponse<ShippingAddressResponse>> findById(Integer shippingId) {
                String cacheKey = "shipping:id:" + shippingId;

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ShippingAddressResponse cachedAddress = fromJson(cachedJson,
                                                                ShippingAddressResponse.class);
                                                return Uni.createFrom().item(ApiResponse.success(
                                                                "Shipping address retrieved successfully",
                                                                cachedAddress));
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findShippingAddressById")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "shipping-address-service")
                                                        .setAttribute("operation", "find_shipping_address_by_id")
                                                        .setAttribute("shipping.id", shippingId.toString())
                                                        .startSpan();

                                        return shippingAddressQueryRepository.findByIdNative(shippingId.longValue())
                                                        .chain(optionalAddress -> {
                                                                if (optionalAddress.isEmpty()) {
                                                                        logger.warn("Shipping address not found with ID: {}",
                                                                                        shippingId);
                                                                        span.setStatus(StatusCode.ERROR,
                                                                                        "Shipping address not found");
                                                                        span.setAttribute("address.found", false);

                                                                        requestsTotal.add(1, Attributes.of(
                                                                                        AttributeKey.stringKey(
                                                                                                        "operation"),
                                                                                        "find_shipping_address_by_id",
                                                                                        AttributeKey.stringKey(
                                                                                                        "status"),
                                                                                        "failed",
                                                                                        AttributeKey.stringKey(
                                                                                                        "error_type"),
                                                                                        "not_found"));

                                                                        throw new ResourceNotFoundException(
                                                                                        "Shipping address not found with ID: "
                                                                                                        + shippingId);
                                                                }

                                                                ShippingAddress address = optionalAddress.get();
                                                                span.setAttribute("address.found", true);
                                                                span.setAttribute("address.city", address.getKota());

                                                                ShippingAddressResponse addressResponse = ShippingAddressResponse
                                                                                .from(address);

                                                                return redisService
                                                                                .setReactive(cacheKey,
                                                                                                toJson(addressResponse))
                                                                                .map(v -> {
                                                                                        logger.info("Cached shipping address for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_shipping_address_by_id",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return ApiResponse.success(
                                                                                                        "Shipping address retrieved successfully",
                                                                                                        addressResponse);
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding shipping address by ID: {}",
                                                                                shippingId, e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_shipping_address_by_id",
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
                                                                                "find_shipping_address_by_id"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponse<ShippingAddressResponse>> findByOrder(Integer orderId) {
                String cacheKey = "shipping:order:" + orderId;

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ShippingAddressResponse cachedAddress = fromJson(cachedJson,
                                                                ShippingAddressResponse.class);
                                                return Uni.createFrom().item(ApiResponse.success(
                                                                "Shipping address retrieved successfully",
                                                                cachedAddress));
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findShippingAddressByOrderId")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "shipping-address-service")
                                                        .setAttribute("operation", "find_shipping_address_by_order_id")
                                                        .setAttribute("order.id", orderId.toString())
                                                        .startSpan();

                                        return shippingAddressQueryRepository.findByOrderId(orderId)
                                                        .chain(optionalAddress -> {
                                                                if (optionalAddress.isEmpty()) {
                                                                        logger.warn("Shipping address not found for order ID: {}",
                                                                                        orderId);
                                                                        span.setStatus(StatusCode.ERROR,
                                                                                        "Shipping address not found");
                                                                        span.setAttribute("address.found", false);

                                                                        requestsTotal.add(1, Attributes.of(
                                                                                        AttributeKey.stringKey(
                                                                                                        "operation"),
                                                                                        "find_shipping_address_by_order_id",
                                                                                        AttributeKey.stringKey(
                                                                                                        "status"),
                                                                                        "failed",
                                                                                        AttributeKey.stringKey(
                                                                                                        "error_type"),
                                                                                        "not_found"));

                                                                        throw new ResourceNotFoundException(
                                                                                        "Shipping address not found for order ID: "
                                                                                                        + orderId);
                                                                }

                                                                ShippingAddress address = optionalAddress.get();
                                                                span.setAttribute("address.found", true);
                                                                span.setAttribute("address.city", address.getKota());

                                                                ShippingAddressResponse addressResponse = ShippingAddressResponse
                                                                                .from(address);

                                                                return redisService
                                                                                .setReactive(cacheKey,
                                                                                                toJson(addressResponse))
                                                                                .map(v -> {
                                                                                        logger.info("Cached shipping address for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_shipping_address_by_order_id",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return ApiResponse.success(
                                                                                                        "Shipping address retrieved successfully",
                                                                                                        addressResponse);
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding shipping address by order ID: {}",
                                                                                orderId, e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_shipping_address_by_order_id",
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
                                                                                "find_shipping_address_by_order_id"));
                                                        });
                                });
        }

        private <T, R> ApiResponsePagination<List<R>> buildPaginatedResponse(
                        PagedResult<T> pagedResult,
                        int pageParam,
                        int sizeParam,
                        String successMessage,
                        Function<T, R> mapper) {

                List<R> data = pagedResult.getData().stream()
                                .map(mapper)
                                .collect(Collectors.toList());

                int totalRecords = pagedResult.getTotalRecords();
                int size = sizeParam > 0 ? sizeParam : 1;
                int totalPages = (int) Math.ceil((double) totalRecords / size);

                PaginationMeta pagination = new PaginationMeta(pageParam, size, totalPages, totalRecords);

                return new ApiResponsePagination<>("success", successMessage, data, pagination);
        }
}
