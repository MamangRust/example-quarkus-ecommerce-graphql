package com.example.service.impl.category.stats;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.config.RedisService;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.category.CategoriesMonthPriceResponse;
import com.example.domain.response.category.CategoriesYearPriceResponse;
import com.example.repository.category.stats.CategoryPriceRepository;
import com.example.service.category.stats.price.CategoryPriceService;

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
public class CategoryPriceServiceImpl implements CategoryPriceService {
        private static final Logger logger = LoggerFactory.getLogger(CategoryPriceServiceImpl.class);

        CategoryPriceRepository categoryPriceRepository;
        OpenTelemetry openTelemetry;
        RedisService redisService;
        ObjectMapper objectMapper;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        private static final long STATS_CACHE_TTL_SECONDS = 300;

        @Inject
        public CategoryPriceServiceImpl(CategoryPriceRepository categoryPriceRepository,
                        OpenTelemetry openTelemetry,
                        RedisService redisService,
                        ObjectMapper objectMapper) {
                this.categoryPriceRepository = categoryPriceRepository;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.objectMapper = objectMapper;
                this.tracer = openTelemetry.getTracer("category-price-stats-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("category-price-stats-service");

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
        public Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPrice(Integer year) {
                String cacheKey = "categories:stats:month-price:" + year;

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<CategoriesMonthPriceResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<CategoriesMonthPriceResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findMonthPrice")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "category-stats-service")
                                                        .setAttribute("operation", "find_month_price_stats")
                                                        .setAttribute("stats.year", year.toString())
                                                        .startSpan();

                                        return categoryPriceRepository.findMonthlyCategoryStats(year)
                                                        .chain(results -> {
                                                                List<CategoriesMonthPriceResponse> responseList = results
                                                                                .stream()
                                                                                .map(CategoriesMonthPriceResponse::from)
                                                                                .collect(Collectors.toList());

                                                                ApiResponse<List<CategoriesMonthPriceResponse>> response = ApiResponse
                                                                                .success(
                                                                                                "Monthly category price stats retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached monthly category stats for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully fetched {} monthly stats",
                                                                                                        responseList.size());
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_month_price_stats",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error fetching monthly category price stats | Year: {}",
                                                                                year, e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_month_price_stats",
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
                                                                                "find_month_price_stats"));
                                                                logger.debug("Fetch monthly category price stats completed in {} seconds",
                                                                                duration);
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPrice(Integer year) {
                String cacheKey = "categories:stats:year-price:" + year;

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<CategoriesYearPriceResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<CategoriesYearPriceResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findYearPrice")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "category-stats-service")
                                                        .setAttribute("operation", "find_year_price_stats")
                                                        .setAttribute("stats.year", year.toString())
                                                        .startSpan();

                                        return categoryPriceRepository.findYearlyCategoryStats(year)
                                                        .chain(results -> {
                                                                List<CategoriesYearPriceResponse> responseList = results
                                                                                .stream()
                                                                                .map(CategoriesYearPriceResponse::from)
                                                                                .collect(Collectors.toList());

                                                                ApiResponse<List<CategoriesYearPriceResponse>> response = ApiResponse
                                                                                .success(
                                                                                                "Yearly category price stats retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached yearly category stats for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully fetched {} yearly stats",
                                                                                                        responseList.size());
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_year_price_stats",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error fetching yearly category price stats | Year: {}",
                                                                                year, e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_year_price_stats",
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
                                                                                "find_year_price_stats"));
                                                                logger.debug("Fetch yearly category price stats completed in {} seconds",
                                                                                duration);
                                                        });
                                });
        }
}
