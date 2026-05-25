package com.example.service.impl.order.statsbymerchant;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.config.RedisService;
import com.example.domain.requests.order.MonthTotalRevenueMerchantRequest;
import com.example.domain.requests.order.YearTotalRevenueMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.order.OrderMonthlyTotalRevenueResponse;
import com.example.domain.response.order.OrderYearlyTotalRevenueResponse;
import com.example.repository.order.statsbymerchant.OrderTotalRevenueByMerchantRepository;
import com.example.service.order.statsbymerchant.OrderTotalRevenueByMerchantService;

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
public class OrderTotalRevenueByMerchantServiceImpl implements OrderTotalRevenueByMerchantService {
    private static final Logger logger = LoggerFactory.getLogger(OrderTotalRevenueByMerchantServiceImpl.class);

    OrderTotalRevenueByMerchantRepository repository;
    OpenTelemetry openTelemetry;
    RedisService redisService;
    ObjectMapper objectMapper;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final long STATS_CACHE_TTL_SECONDS = 600;

    @Inject
    public OrderTotalRevenueByMerchantServiceImpl(OrderTotalRevenueByMerchantRepository repository,
            OpenTelemetry openTelemetry,
            RedisService redisService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("order-revenue-merchant-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("order-revenue-merchant-service");

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
    public Uni<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> findMonthlyStatsByMerchant(
            MonthTotalRevenueMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
            logger.error("❌ MerchantId, Year, or Month is null | req: {}", req);
            return Uni.createFrom().item(ApiResponse.error("MerchantId, Year, and Month must not be null", null));
        }

        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12", null));
        }

        String cacheKey = String.format("order:revenue:merchant:monthly:%d:%d:%d", req.getMerchantId(), req.getYear(),
                req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderMonthlyTotalRevenueResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findMonthlyStatsByMerchant")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-revenue-merchant-service")
                            .setAttribute("operation", "find_monthly_revenue_stats_by_merchant")
                            .setAttribute("merchant.id", req.getMerchantId().toString())
                            .setAttribute("year", req.getYear().toString())
                            .setAttribute("month", req.getMonth().toString())
                            .startSpan();

                    LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                    LocalDate next = current.plusMonths(1);

                    return repository.findMonthlyTotalRevenueByMerchant(
                            req.getMerchantId().longValue(),
                            req.getYear(), req.getMonth(),
                            next.getYear(), next.getMonthValue())
                            .chain(rawData -> {
                                List<OrderMonthlyTotalRevenueResponse> responseList = rawData.stream()
                                        .map(OrderMonthlyTotalRevenueResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<OrderMonthlyTotalRevenueResponse>> response = ApiResponse.success(
                                        "Monthly order stats for merchant retrieved successfully", responseList);

                                return redisService
                                        .setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached monthly revenue for merchant key: {}", cacheKey);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"),
                                                    "find_monthly_revenue_stats_by_merchant",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error fetching monthly order stats for merchantId={}",
                                        req.getMerchantId(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_revenue_stats_by_merchant",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_revenue_stats_by_merchant"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> findYearlyStatsByMerchant(
            YearTotalRevenueMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null) {
            logger.error("❌ MerchantId or Year is null | req: {}", req);
            return Uni.createFrom().item(ApiResponse.error("MerchantId and Year must not be null", null));
        }

        String cacheKey = String.format("order:revenue:merchant:yearly:%d:%d", req.getMerchantId(), req.getYear());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderYearlyTotalRevenueResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderYearlyTotalRevenueResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findYearlyStatsByMerchant")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-revenue-merchant-service")
                            .setAttribute("operation", "find_yearly_revenue_stats_by_merchant")
                            .setAttribute("merchant.id", req.getMerchantId().toString())
                            .setAttribute("year", req.getYear().toString())
                            .startSpan();

                    return repository.findYearlyTotalRevenueByMerchant(req.getMerchantId().longValue(), req.getYear())
                            .chain(rawData -> {
                                List<OrderYearlyTotalRevenueResponse> responseList = rawData.stream()
                                        .map(OrderYearlyTotalRevenueResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<OrderYearlyTotalRevenueResponse>> response = ApiResponse.success(
                                        "Yearly order stats for merchant retrieved successfully", responseList);

                                return redisService
                                        .setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached yearly revenue for merchant key: {}", cacheKey);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"),
                                                    "find_yearly_revenue_stats_by_merchant",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error fetching yearly order stats for merchantId={}", req.getMerchantId(),
                                        e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_revenue_stats_by_merchant",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_revenue_stats_by_merchant"));
                            });
                });
    }
}
