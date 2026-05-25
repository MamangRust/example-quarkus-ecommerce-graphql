package com.example.service.impl.order.stats;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.config.RedisService;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.order.OrderMonthlyResponse;
import com.example.domain.response.order.OrderYearlyResponse;
import com.example.repository.order.stats.OrderSoldOutRepository;
import com.example.service.order.stats.OrderSoldoutService;

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
public class OrderSoldOutServiceImpl implements OrderSoldoutService {
        private static final Logger logger = LoggerFactory.getLogger(OrderSoldOutServiceImpl.class);

        OrderSoldOutRepository orderSoldOutRepository;
        OpenTelemetry openTelemetry;
        RedisService redisService;
        ObjectMapper objectMapper;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        private static final long STATS_CACHE_TTL_SECONDS = 600;

        @Inject
        public OrderSoldOutServiceImpl(OrderSoldOutRepository orderSoldOutRepository,
                        OpenTelemetry openTelemetry,
                        RedisService redisService,
                        ObjectMapper objectMapper) {
                this.orderSoldOutRepository = orderSoldOutRepository;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.objectMapper = objectMapper;
                this.tracer = openTelemetry.getTracer("order-soldout-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("order-soldout-service");

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
        public Uni<ApiResponse<List<OrderMonthlyResponse>>> findMonthlyOrders(Integer yearMonth) {
                String cacheKey = "order:soldout:monthly:" + yearMonth;

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
                                        Span span = tracer.spanBuilder("findMonthlyOrders")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "order-soldout-service")
                                                        .setAttribute("operation", "find_monthly_orders")
                                                        .setAttribute("yearMonth", yearMonth.toString())
                                                        .startSpan();

                                        return orderSoldOutRepository.findMonthlyOrders(yearMonth)
                                                        .chain(rawData -> {
                                                                List<OrderMonthlyResponse> responseList = rawData
                                                                                .stream()
                                                                                .map(OrderMonthlyResponse::from)
                                                                                .collect(Collectors.toList());

                                                                ApiResponse<List<OrderMonthlyResponse>> response = ApiResponse
                                                                                .success(
                                                                                                "Monthly order data retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached monthly stats for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_monthly_orders",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error fetching monthly orders for yearMonth={}",
                                                                                yearMonth, e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_monthly_orders",
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
                                                                                "find_monthly_orders"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponse<List<OrderYearlyResponse>>> findYearlyOrders(Integer yearMonth) {
                String cacheKey = "order:soldout:yearly:" + yearMonth;

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
                                        Span span = tracer.spanBuilder("findYearlyOrders")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "order-soldout-service")
                                                        .setAttribute("operation", "find_yearly_orders")
                                                        .setAttribute("yearMonth", yearMonth.toString())
                                                        .startSpan();

                                        return orderSoldOutRepository.findYearlyOrders(yearMonth)
                                                        .chain(rawData -> {
                                                                List<OrderYearlyResponse> responseList = rawData
                                                                                .stream()
                                                                                .map(OrderYearlyResponse::from)
                                                                                .collect(Collectors.toList());

                                                                ApiResponse<List<OrderYearlyResponse>> response = ApiResponse
                                                                                .success(
                                                                                                "Yearly order data retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached yearly stats for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_yearly_orders",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error fetching yearly orders for yearMonth={}",
                                                                                yearMonth, e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_yearly_orders",
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
                                                                                "find_yearly_orders"));
                                                        });
                                });
        }
}
