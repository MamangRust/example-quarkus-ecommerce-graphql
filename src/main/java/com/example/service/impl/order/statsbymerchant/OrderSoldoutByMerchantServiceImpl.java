package com.example.service.impl.order.statsbymerchant;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.config.RedisService;
import com.example.domain.requests.order.MonthOrderMerchantRequest;
import com.example.domain.requests.order.YearOrderMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.order.OrderMonthlyResponse;
import com.example.domain.response.order.OrderYearlyResponse;
import com.example.repository.order.statsbymerchant.OrderSoldOutByMerchantRepository;
import com.example.service.order.statsbymerchant.OrderSoldOutByMerchantService;

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
public class OrderSoldoutByMerchantServiceImpl implements OrderSoldOutByMerchantService {
    private static final Logger logger = LoggerFactory.getLogger(OrderSoldoutByMerchantServiceImpl.class);

    OrderSoldOutByMerchantRepository orderSoldOutByMerchantRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;
    ObjectMapper objectMapper;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final long STATS_CACHE_TTL_SECONDS = 600;

    @Inject
    public OrderSoldoutByMerchantServiceImpl(OrderSoldOutByMerchantRepository orderSoldOutByMerchantRepository,
            OpenTelemetry openTelemetry,
            RedisService redisService,
            ObjectMapper objectMapper) {
        this.orderSoldOutByMerchantRepository = orderSoldOutByMerchantRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("order-soldout-merchant-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("order-soldout-merchant-service");

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
            logger.error("Error deserializing JSON to object", e);
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    @Override
    public Uni<ApiResponse<List<OrderMonthlyResponse>>> findMonthlyOrdersByMerchant(MonthOrderMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null) {
            logger.error("❌ Merchant ID or Year is null | req: {}", req);
            return Uni.createFrom().item(ApiResponse.error("Merchant ID and Year are required", null));
        }

        String cacheKey = String.format("order:soldout:merchant:monthly:%d:%d", req.getMerchantId(), req.getYear());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderMonthlyResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderMonthlyResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findMonthlyOrdersByMerchant")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-soldout-merchant-service")
                            .setAttribute("operation", "find_monthly_orders_by_merchant")
                            .setAttribute("merchant.id", req.getMerchantId().toString())
                            .setAttribute("year", req.getYear().toString())
                            .startSpan();

                    return orderSoldOutByMerchantRepository
                            .findMonthlyOrdersByMerchant(req.getMerchantId(), req.getYear())
                            .chain(rawData -> {
                                List<OrderMonthlyResponse> responseList = rawData.stream()
                                        .map(OrderMonthlyResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<OrderMonthlyResponse>> response = ApiResponse.success(
                                        "Monthly order stats by merchant retrieved successfully", responseList);

                                return redisService
                                        .setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached monthly stats for merchant key: {}", cacheKey);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"),
                                                    "find_monthly_orders_by_merchant",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error fetching monthly orders for merchant | merchantId={}, year={}",
                                        req.getMerchantId(), req.getYear(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_orders_by_merchant",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_orders_by_merchant"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<List<OrderYearlyResponse>>> findYearlyOrdersByMerchant(YearOrderMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null) {
            logger.error("❌ Merchant ID or Year is null | req: {}", req);
            return Uni.createFrom().item(ApiResponse.error("Merchant ID and Year are required", null));
        }

        String cacheKey = String.format("order:soldout:merchant:yearly:%d:%d", req.getMerchantId(), req.getYear());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderYearlyResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderYearlyResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findYearlyOrdersByMerchant")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-soldout-merchant-service")
                            .setAttribute("operation", "find_yearly_orders_by_merchant")
                            .setAttribute("merchant.id", req.getMerchantId().toString())
                            .setAttribute("year", req.getYear().toString())
                            .startSpan();

                    return orderSoldOutByMerchantRepository
                            .findYearlyOrdersByMerchant(req.getMerchantId(), req.getYear())
                            .chain(rawData -> {
                                List<OrderYearlyResponse> responseList = rawData.stream()
                                        .map(OrderYearlyResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<OrderYearlyResponse>> response = ApiResponse.success(
                                        "Yearly order stats by merchant retrieved successfully", responseList);

                                return redisService
                                        .setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached yearly stats for merchant key: {}", cacheKey);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"),
                                                    "find_yearly_orders_by_merchant",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error fetching yearly orders for merchant | merchantId={}, year={}",
                                        req.getMerchantId(), req.getYear(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_orders_by_merchant",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_orders_by_merchant"));
                            });
                });
    }
}
