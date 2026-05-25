package com.example.service.impl.slider;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.config.RedisService;
import com.example.domain.requests.slider.FindAllSliderRequest;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.api.PagedResult;
import com.example.domain.response.api.PaginationMeta;
import com.example.domain.response.slider.SliderResponse;
import com.example.domain.response.slider.SliderResponseDeleteAt;
import com.example.repository.slider.SliderQueryRepository;
import com.example.service.slider.SliderQueryService;

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
public class SliderQueryServiceImpl implements SliderQueryService {
        private static final Logger logger = LoggerFactory.getLogger(SliderQueryServiceImpl.class);

        SliderQueryRepository sliderQueryRepository;
        OpenTelemetry openTelemetry;
        RedisService redisService;
        ObjectMapper objectMapper;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        private static final long LIST_CACHE_TTL_SECONDS = 300;

        @Inject
        public SliderQueryServiceImpl(SliderQueryRepository sliderQueryRepository,
                        OpenTelemetry openTelemetry,
                        RedisService redisService,
                        ObjectMapper objectMapper) {
                this.sliderQueryRepository = sliderQueryRepository;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.objectMapper = objectMapper;
                this.tracer = openTelemetry.getTracer("slider-query-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("slider-query-service");

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
        public Uni<ApiResponsePagination<List<SliderResponse>>> findAll(FindAllSliderRequest req) {
                String cacheKey = String.format("slider:all:%d:%d:%s", req.getPage(), req.getPageSize(),
                                req.getSearch() != null ? req.getSearch() : "");

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<SliderResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<SliderResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findAllSliders")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "slider-service")
                                                        .setAttribute("operation", "find_all_sliders")
                                                        .startSpan();

                                        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                                        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                                        String search = (req.getSearch() != null && !req.getSearch().isEmpty())
                                                        ? req.getSearch()
                                                        : "";

                                        return sliderQueryRepository.findSliders(search, page, size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("slider.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("slider.page", req.getPage());
                                                                span.setAttribute("slider.size", req.getPageSize());

                                                                ApiResponsePagination<List<SliderResponse>> response = buildPaginatedResponse(
                                                                                pagedResult, req.getPage(),
                                                                                req.getPageSize(),
                                                                                "Sliders retrieved successfully",
                                                                                SliderResponse::from);

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
                                                                                                                        "find_all_sliders",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding all sliders", e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_all_sliders",
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
                                                                                "find_all_sliders"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponsePagination<List<SliderResponseDeleteAt>>> findByActive(FindAllSliderRequest req) {
                String cacheKey = String.format("slider:active:%d:%d:%s", req.getPage(), req.getPageSize(),
                                req.getSearch() != null ? req.getSearch() : "");

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<SliderResponseDeleteAt>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<SliderResponseDeleteAt>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findActiveSliders")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "slider-service")
                                                        .setAttribute("operation", "find_active_sliders")
                                                        .startSpan();

                                        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                                        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                                        String search = (req.getSearch() != null && !req.getSearch().isEmpty())
                                                        ? req.getSearch()
                                                        : "";

                                        return sliderQueryRepository.findActiveSliders(search, page, size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("slider.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("slider.page", req.getPage());
                                                                span.setAttribute("slider.size", req.getPageSize());

                                                                ApiResponsePagination<List<SliderResponseDeleteAt>> response = buildPaginatedResponse(
                                                                                pagedResult, req.getPage(),
                                                                                req.getPageSize(),
                                                                                "Active sliders retrieved successfully",
                                                                                SliderResponseDeleteAt::from);

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
                                                                                                                        "find_active_sliders",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding active sliders", e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_active_sliders",
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
                                                                                "find_active_sliders"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponsePagination<List<SliderResponseDeleteAt>>> findByTrashed(FindAllSliderRequest req) {
                String cacheKey = String.format("slider:trashed:%d:%d:%s", req.getPage(), req.getPageSize(),
                                req.getSearch() != null ? req.getSearch() : "");

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<SliderResponseDeleteAt>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<SliderResponseDeleteAt>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findTrashedSliders")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "slider-service")
                                                        .setAttribute("operation", "find_trashed_sliders")
                                                        .startSpan();

                                        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                                        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                                        String search = (req.getSearch() != null && !req.getSearch().isEmpty())
                                                        ? req.getSearch()
                                                        : "";

                                        return sliderQueryRepository.findTrashedSliders(search, page, size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("slider.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("slider.page", req.getPage());
                                                                span.setAttribute("slider.size", req.getPageSize());

                                                                ApiResponsePagination<List<SliderResponseDeleteAt>> response = buildPaginatedResponse(
                                                                                pagedResult, req.getPage(),
                                                                                req.getPageSize(),
                                                                                "Trashed sliders retrieved successfully",
                                                                                SliderResponseDeleteAt::from);

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
                                                                                                                        "find_trashed_sliders",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding trashed sliders", e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_trashed_sliders",
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
                                                                                "find_trashed_sliders"));
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
