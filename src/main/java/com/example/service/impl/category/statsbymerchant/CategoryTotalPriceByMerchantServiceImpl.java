package com.example.service.impl.category.statsbymerchant;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.config.RedisService;
import com.example.domain.requests.category.MonthTotalPriceMerchantRequest;
import com.example.domain.requests.category.YearTotalPriceMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.category.CategoriesMonthlyTotalPriceResponse;
import com.example.domain.response.category.CategoriesYearlyTotalPriceResponse;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.category.statsbymerchant.CategoryTotalPriceByMerchantRepository;
import com.example.service.category.stats.totalprice.CategoryTotalPriceByMerchantService;

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
public class CategoryTotalPriceByMerchantServiceImpl implements CategoryTotalPriceByMerchantService {
        private static final Logger logger = LoggerFactory.getLogger(CategoryTotalPriceByMerchantServiceImpl.class);

        CategoryTotalPriceByMerchantRepository categoryTotalPriceByMerchantRepository;
        OpenTelemetry openTelemetry;
        RedisService redisService;
        ObjectMapper objectMapper;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        private static final long STATS_CACHE_TTL_SECONDS = 300;

        @Inject
        public CategoryTotalPriceByMerchantServiceImpl(
                        CategoryTotalPriceByMerchantRepository categoryTotalPriceByMerchantRepository,
                        OpenTelemetry openTelemetry,
                        RedisService redisService,
                        ObjectMapper objectMapper) {
                this.categoryTotalPriceByMerchantRepository = categoryTotalPriceByMerchantRepository;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.objectMapper = objectMapper;
                this.tracer = openTelemetry.getTracer("category-total-price-by-merchant-stats-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("category-total-price-by-merchant-stats-service");

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
        public Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPriceByMerchant(
                        MonthTotalPriceMerchantRequest req) {
                if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
                        logger.error("❌ MerchantId, Year or Month is null | req: {}", req);
                        throw new ResourceNotFoundException("MerchantId, Year and Month must not be null");
                }

                String cacheKey = String.format("categories:stats:monthly-total-price-by-merchant:%d:%d:%d",
                                req.getMerchantId(), req.getYear(), req.getMonth());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findMonthlyTotalPriceByMerchant")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "category-stats-service")
                                                        .setAttribute("operation",
                                                                        "find_monthly_total_price_by_merchant")
                                                        .setAttribute("stats.merchant.id",
                                                                        req.getMerchantId().toString())
                                                        .setAttribute("stats.year", req.getYear().toString())
                                                        .setAttribute("stats.month", req.getMonth().toString())
                                                        .startSpan();

                                        LocalDate currentMonth = LocalDate.of(req.getYear(), req.getMonth(), 1);
                                        LocalDate nextMonth = currentMonth.plusMonths(1);

                                        return categoryTotalPriceByMerchantRepository.findMonthlyTotalPriceByMerchant(
                                                        req.getMerchantId(),
                                                        req.getYear(),
                                                        req.getMonth(),
                                                        nextMonth.getYear(),
                                                        nextMonth.getMonthValue())
                                                        .chain(results -> {
                                                                List<CategoriesMonthlyTotalPriceResponse> responseList = results
                                                                                .stream()
                                                                                .map(CategoriesMonthlyTotalPriceResponse::from)
                                                                                .collect(Collectors.toList());

                                                                ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> response = ApiResponse
                                                                                .success(
                                                                                                "Monthly total price by merchant retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached monthly total price stats by merchant for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully fetched {} monthly total price stats for merchant {}",
                                                                                                        responseList.size(),
                                                                                                        req.getMerchantId());
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_monthly_total_price_by_merchant",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error fetching monthly total price by merchant | MerchantId: {}, Year: {}, Month: {}",
                                                                                req.getMerchantId(), req.getYear(),
                                                                                req.getMonth(), e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_monthly_total_price_by_merchant",
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
                                                                                "find_monthly_total_price_by_merchant"));
                                                                logger.debug("Fetch monthly total price by merchant completed in {} seconds",
                                                                                duration);
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPriceByMerchant(
                        YearTotalPriceMerchantRequest req) {
                if (req.getMerchantId() == null || req.getYear() == null) {
                        logger.error("❌ MerchantId or Year is null | req: {}", req);
                        throw new ResourceNotFoundException("MerchantId and Year must not be null");
                }

                String cacheKey = String.format("categories:stats:yearly-total-price-by-merchant:%d:%d",
                                req.getMerchantId(), req.getYear());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<CategoriesYearlyTotalPriceResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findYearlyTotalPriceByMerchant")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "category-stats-service")
                                                        .setAttribute("operation",
                                                                        "find_yearly_total_price_by_merchant")
                                                        .setAttribute("stats.merchant.id",
                                                                        req.getMerchantId().toString())
                                                        .setAttribute("stats.year", req.getYear().toString())
                                                        .startSpan();

                                        return categoryTotalPriceByMerchantRepository
                                                        .findYearlyTotalPriceByMerchant(req.getMerchantId(),
                                                                        req.getYear())
                                                        .chain(results -> {
                                                                List<CategoriesYearlyTotalPriceResponse> responseList = results
                                                                                .stream()
                                                                                .map(CategoriesYearlyTotalPriceResponse::from)
                                                                                .collect(Collectors.toList());

                                                                ApiResponse<List<CategoriesYearlyTotalPriceResponse>> response = ApiResponse
                                                                                .success(
                                                                                                "Yearly total price by merchant retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached yearly total price stats by merchant for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully fetched {} yearly total price stats for merchant {}",
                                                                                                        responseList.size(),
                                                                                                        req.getMerchantId());
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_yearly_total_price_by_merchant",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error fetching yearly total price by merchant | MerchantId: {}, Year: {}",
                                                                                req.getMerchantId(), req.getYear(), e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_yearly_total_price_by_merchant",
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
                                                                                "find_yearly_total_price_by_merchant"));
                                                                logger.debug("Fetch yearly total price by merchant completed in {} seconds",
                                                                                duration);
                                                        });
                                });
        }
}
