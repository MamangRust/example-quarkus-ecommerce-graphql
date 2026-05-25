package com.example.service.impl.category.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.config.RedisService;
import com.example.domain.requests.category.MonthTotalPriceRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.category.CategoriesMonthlyTotalPriceResponse;
import com.example.domain.response.category.CategoriesYearlyTotalPriceResponse;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.category.stats.CategoryTotalPriceRepository;
import com.example.service.category.stats.totalprice.CategoryTotalPriceService;

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
public class CategoryTotalPriceServiceImpl implements CategoryTotalPriceService {
        private static final Logger logger = LoggerFactory.getLogger(CategoryTotalPriceServiceImpl.class);

        CategoryTotalPriceRepository categoryTotalPriceRepository;
        OpenTelemetry openTelemetry;
        RedisService redisService;
        ObjectMapper objectMapper;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        private static final long STATS_CACHE_TTL_SECONDS = 300;

        @Inject
        public CategoryTotalPriceServiceImpl(CategoryTotalPriceRepository categoryTotalPriceRepository,
                        OpenTelemetry openTelemetry,
                        RedisService redisService,
                        ObjectMapper objectMapper) {
                this.categoryTotalPriceRepository = categoryTotalPriceRepository;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.objectMapper = objectMapper;
                this.tracer = openTelemetry.getTracer("category-total-price-stats-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("category-total-price-stats-service");

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
        public Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPrice(
                        MonthTotalPriceRequest req) {
                if (req.getYear() == null || req.getMonth() == null) {
                        logger.error("❌ Year or Month is null | req: {}", req);
                        throw new ResourceNotFoundException("Year and Month must not be null");
                }

                String cacheKey = String.format("categories:stats:monthly-total-price:%d:%d", req.getYear(),
                                req.getMonth());

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
                                        Span span = tracer.spanBuilder("findMonthlyTotalPrice")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "category-stats-service")
                                                        .setAttribute("operation", "find_monthly_total_price_stats")
                                                        .setAttribute("stats.year", req.getYear().toString())
                                                        .setAttribute("stats.month", req.getMonth().toString())
                                                        .startSpan();

                                        LocalDate currentMonth = LocalDate.of(req.getYear(), req.getMonth(), 1);
                                        LocalDate nextMonth = currentMonth.plusMonths(1);

                                        return categoryTotalPriceRepository.findMonthlyTotalPrice(
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
                                                                                                "Monthly total price stats retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached monthly total price stats for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully fetched {} monthly total price stats",
                                                                                                        responseList.size());
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_monthly_total_price_stats",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error fetching monthly total category price | Year: {}, Month: {}",
                                                                                req.getYear(), req.getMonth(), e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_monthly_total_price_stats",
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
                                                                                "find_monthly_total_price_stats"));
                                                                logger.debug("Fetch monthly total category price completed in {} seconds",
                                                                                duration);
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPrice(Integer year) {
                String cacheKey = "categories:stats:yearly-total-price:" + year;

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
                                        Span span = tracer.spanBuilder("findYearlyTotalPrice")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "category-stats-service")
                                                        .setAttribute("operation", "find_yearly_total_price_stats")
                                                        .setAttribute("stats.year", year.toString())
                                                        .startSpan();

                                        return categoryTotalPriceRepository.findYearlyTotalPrice(year)
                                                        .chain(results -> {
                                                                List<CategoriesYearlyTotalPriceResponse> responseList = results
                                                                                .stream()
                                                                                .map(CategoriesYearlyTotalPriceResponse::from)
                                                                                .collect(Collectors.toList());

                                                                ApiResponse<List<CategoriesYearlyTotalPriceResponse>> response = ApiResponse
                                                                                .success(
                                                                                                "Yearly total price stats retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached yearly total price stats for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully fetched {} yearly total price stats",
                                                                                                        responseList.size());
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_yearly_total_price_stats",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error fetching yearly total category price | Year: {}",
                                                                                year, e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_yearly_total_price_stats",
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
                                                                                "find_yearly_total_price_stats"));
                                                                logger.debug("Fetch yearly total category price completed in {} seconds",
                                                                                duration);
                                                        });
                                });
        }
}
