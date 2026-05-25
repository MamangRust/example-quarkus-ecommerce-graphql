package com.example.service.impl.banner;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.config.RedisService;
import com.example.domain.requests.banner.FindAllBannerRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.api.PagedResult;
import com.example.domain.response.api.PaginationMeta;
import com.example.domain.response.banner.BannerResponse;
import com.example.domain.response.banner.BannerResponseDeleteAt;
import com.example.repository.banner.BannerQueryRepository;
import com.example.service.banner.BannerQueryService;

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
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class BannerQueryServiceImpl implements BannerQueryService {
        private static final Logger logger = LoggerFactory.getLogger(BannerQueryServiceImpl.class);

        BannerQueryRepository bannerQueryRepository;
        OpenTelemetry openTelemetry;
        RedisService redisService;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        private final ObjectMapper objectMapper;

        private static final long LIST_CACHE_TTL_SECONDS = 300;

        @Inject
        public BannerQueryServiceImpl(BannerQueryRepository bannerQueryRepository, OpenTelemetry openTelemetry,
                        RedisService redisService,
                        ObjectMapper objectMapper) {
                this.bannerQueryRepository = bannerQueryRepository;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.objectMapper = objectMapper;
                this.tracer = openTelemetry.getTracer("banner-query-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("banner-query-service");

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
        public Uni<ApiResponsePagination<List<BannerResponse>>> findAll(FindAllBannerRequest request) {
                String cacheKey = String.format("banners:all:%d:%d:%s", request.getPage(), request.getPageSize(),
                                request.getSearch());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<BannerResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<BannerResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findAllBanners")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "banner-service")
                                                        .setAttribute("operation", "find_all_banners")
                                                        .startSpan();

                                        int page = request.getPage() > 0 ? request.getPage() - 1 : 0;
                                        int size = request.getPageSize() > 0 ? request.getPageSize() : 10;
                                        String search = (request.getSearch() != null && !request.getSearch().isEmpty())
                                                        ? request.getSearch()
                                                        : "";

                                        return bannerQueryRepository.findBanners(search, page, size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("banner.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("banner.page", request.getPage());
                                                                span.setAttribute("banner.size", request.getPageSize());

                                                                ApiResponsePagination<List<BannerResponse>> response = buildPaginatedResponse(
                                                                                pagedResult, request,
                                                                                "Banners retrieved successfully",
                                                                                BannerResponse::from);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                LIST_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully retrieved {} banners",
                                                                                                        pagedResult.getTotalRecords());
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_all_banners",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding all banners", e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_all_banners",
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
                                                                                "find_all_banners"));
                                                                logger.debug("Find all banners operation completed in {} seconds",
                                                                                duration);
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponsePagination<List<BannerResponseDeleteAt>>> findByActive(FindAllBannerRequest request) {
                String cacheKey = String.format("banners:active:%d:%d:%s", request.getPage(), request.getPageSize(),
                                request.getSearch());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<BannerResponseDeleteAt>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<BannerResponseDeleteAt>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findActiveBanners")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "banner-service")
                                                        .setAttribute("operation", "find_active_banners")
                                                        .startSpan();

                                        int page = request.getPage() > 0 ? request.getPage() - 1 : 0;
                                        int size = request.getPageSize() > 0 ? request.getPageSize() : 10;
                                        String search = (request.getSearch() != null && !request.getSearch().isEmpty())
                                                        ? request.getSearch()
                                                        : "";

                                        return bannerQueryRepository.findActiveBanners(search, page, size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("banner.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("banner.page", request.getPage());
                                                                span.setAttribute("banner.size", request.getPageSize());

                                                                ApiResponsePagination<List<BannerResponseDeleteAt>> response = buildPaginatedResponse(
                                                                                pagedResult, request,
                                                                                "Active banners retrieved successfully",
                                                                                BannerResponseDeleteAt::from);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                LIST_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully retrieved {} active banners",
                                                                                                        pagedResult.getTotalRecords());
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_active_banners",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding active banners", e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_active_banners",
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
                                                                                "find_active_banners"));
                                                                logger.debug("Find active banners operation completed in {} seconds",
                                                                                duration);
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponsePagination<List<BannerResponseDeleteAt>>> findByTrashed(FindAllBannerRequest request) {
                String cacheKey = String.format("banners:trashed:%d:%d:%s", request.getPage(), request.getPageSize(),
                                request.getSearch());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<BannerResponseDeleteAt>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<BannerResponseDeleteAt>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findTrashedBanners")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "banner-service")
                                                        .setAttribute("operation", "find_trashed_banners")
                                                        .startSpan();

                                        int page = request.getPage() > 0 ? request.getPage() - 1 : 0;
                                        int size = request.getPageSize() > 0 ? request.getPageSize() : 10;
                                        String search = (request.getSearch() != null && !request.getSearch().isEmpty())
                                                        ? request.getSearch()
                                                        : "";

                                        return bannerQueryRepository.findTrashedBanners(search, page, size)
                                                        .chain(pagedResult -> {
                                                                span.setAttribute("banner.count",
                                                                                pagedResult.getTotalRecords());
                                                                span.setAttribute("banner.page", request.getPage());
                                                                span.setAttribute("banner.size", request.getPageSize());

                                                                ApiResponsePagination<List<BannerResponseDeleteAt>> response = buildPaginatedResponse(
                                                                                pagedResult, request,
                                                                                "Trashed banners retrieved successfully",
                                                                                BannerResponseDeleteAt::from);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                LIST_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully retrieved {} trashed banners",
                                                                                                        pagedResult.getTotalRecords());
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_trashed_banners",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding trashed banners", e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_trashed_banners",
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
                                                                                "find_trashed_banners"));
                                                                logger.debug("Find trashed banners operation completed in {} seconds",
                                                                                duration);
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponse<BannerResponse>> findById(Long id) {
                String cacheKey = "banner:" + id;

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                BannerResponse cachedBanner = fromJson(cachedJson,
                                                                BannerResponse.class);
                                                return Uni.createFrom().item(
                                                                ApiResponse.success("Banner found", cachedBanner));
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findBannerById")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "banner-service")
                                                        .setAttribute("operation", "find_banner_by_id")
                                                        .setAttribute("banner.id", id.toString())
                                                        .startSpan();

                                        return bannerQueryRepository.findById(id)
                                                        .chain(banner -> {
                                                                if (banner == null) {
                                                                        logger.warn("Banner not found with id: {}", id);
                                                                        span.setStatus(StatusCode.ERROR,
                                                                                        "Banner not found");
                                                                        span.setAttribute("banner.found", false);

                                                                        requestsTotal.add(1, Attributes.of(
                                                                                        AttributeKey.stringKey(
                                                                                                        "operation"),
                                                                                        "find_banner_by_id",
                                                                                        AttributeKey.stringKey(
                                                                                                        "status"),
                                                                                        "failed",
                                                                                        AttributeKey.stringKey(
                                                                                                        "error_type"),
                                                                                        "not_found"));

                                                                        throw new NotFoundException(
                                                                                        "Banner not found with id: "
                                                                                                        + id);
                                                                }

                                                                span.setAttribute("banner.found", true);
                                                                span.setAttribute("banner.name", banner.getName());

                                                                BannerResponse bannerResponse = BannerResponse
                                                                                .from(banner);

                                                                return redisService
                                                                                .setReactive(cacheKey,
                                                                                                toJson(bannerResponse))
                                                                                .map(v -> {
                                                                                        logger.info("Cached banner for key: {}",
                                                                                                        cacheKey);
                                                                                        logger.info("Successfully found banner with id: {} and name: {}",
                                                                                                        id,
                                                                                                        banner.getName());
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_banner_by_id",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));

                                                                                        return ApiResponse.success(
                                                                                                        "Banner found",
                                                                                                        bannerResponse);
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding banner by id: {}", id, e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_banner_by_id",
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
                                                                                "find_banner_by_id"));
                                                                logger.debug("Find banner by id operation completed in {} seconds",
                                                                                duration);
                                                        });
                                });
        }

        private <T, R> ApiResponsePagination<List<R>> buildPaginatedResponse(
                        PagedResult<T> pagedResult,
                        FindAllBannerRequest request,
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
