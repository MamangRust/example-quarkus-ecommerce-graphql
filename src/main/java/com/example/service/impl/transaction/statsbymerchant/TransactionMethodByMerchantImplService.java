package com.example.service.impl.transaction.statsbymerchant;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.config.RedisService;
import com.example.domain.requests.transactions.MonthMethodTransactionMerchantRequest;
import com.example.domain.requests.transactions.YearMethodTransactionMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.transaction.TransactionMonthlyMethodResponse;
import com.example.domain.response.transaction.TransactionYearlyMethodResponse;
import com.example.repository.transaction.statsbymerchant.TransactionMethodByMerchantRepository;
import com.example.service.transaction.statsbymerchant.TransactionMethodByMerchantService;

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
public class TransactionMethodByMerchantImplService implements TransactionMethodByMerchantService {
        private static final Logger logger = LoggerFactory.getLogger(TransactionMethodByMerchantImplService.class);

        TransactionMethodByMerchantRepository transactionMethodByMerchantRepository;
        OpenTelemetry openTelemetry;
        RedisService redisService;
        ObjectMapper objectMapper;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        private static final long STATS_CACHE_TTL_SECONDS = 300;

        @Inject
        public TransactionMethodByMerchantImplService(
                        TransactionMethodByMerchantRepository transactionMethodByMerchantRepository,
                        OpenTelemetry openTelemetry,
                        RedisService redisService,
                        ObjectMapper objectMapper) {
                this.transactionMethodByMerchantRepository = transactionMethodByMerchantRepository;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.objectMapper = objectMapper;
                this.tracer = openTelemetry.getTracer("transaction-method-merchant-stats-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("transaction-method-merchant-stats-service");

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
        public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodByMerchantSuccess(
                        MonthMethodTransactionMerchantRequest req) {
                if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
                        return Uni.createFrom()
                                        .item(ApiResponse.error("Merchant ID, Year, and Month must not be null", null));
                }
                if (req.getMonth() < 1 || req.getMonth() > 12) {
                        return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12", null));
                }

                String cacheKey = String.format("transaction:method:merchant:monthly:success:%d:%d:%d",
                                req.getMerchantId(), req.getYear(), req.getMonth());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionMonthlyMethodResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findMonthlyMethodByMerchantSuccess")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "transaction-stats-service")
                                                        .setAttribute("operation",
                                                                        "find_monthly_method_by_merchant_success")
                                                        .setAttribute("merchant.id", req.getMerchantId().toString())
                                                        .setAttribute("year", req.getYear().toString())
                                                        .setAttribute("month", req.getMonth().toString())
                                                        .startSpan();

                                        LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                                        LocalDate prev = current.minusMonths(1);

                                        return transactionMethodByMerchantRepository
                                                        .findMonthlyTransactionMethodsSuccessByMerchant(
                                                                        req.getMerchantId().longValue(), req.getYear(),
                                                                        req.getMonth(), prev.getYear(),
                                                                        prev.getMonthValue())
                                                        .chain(rawData -> {
                                                                List<TransactionMonthlyMethodResponse> responseList = rawData
                                                                                .stream()
                                                                                .map(TransactionMonthlyMethodResponse::from)
                                                                                .collect(Collectors.toList());

                                                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = ApiResponse
                                                                                .success(
                                                                                                "Monthly success transaction by method & merchant retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_monthly_method_by_merchant_success",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding monthly success transaction by method & merchant",
                                                                                e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_monthly_method_by_merchant_success",
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
                                                                                "find_monthly_method_by_merchant_success"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodByMerchantFailed(
                        MonthMethodTransactionMerchantRequest req) {
                if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
                        return Uni.createFrom()
                                        .item(ApiResponse.error("Merchant ID, Year, and Month must not be null", null));
                }
                if (req.getMonth() < 1 || req.getMonth() > 12) {
                        return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12", null));
                }

                String cacheKey = String.format("transaction:method:merchant:monthly:failed:%d:%d:%d",
                                req.getMerchantId(), req.getYear(), req.getMonth());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionMonthlyMethodResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findMonthlyMethodByMerchantFailed")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "transaction-stats-service")
                                                        .setAttribute("operation",
                                                                        "find_monthly_method_by_merchant_failed")
                                                        .setAttribute("merchant.id", req.getMerchantId().toString())
                                                        .setAttribute("year", req.getYear().toString())
                                                        .setAttribute("month", req.getMonth().toString())
                                                        .startSpan();

                                        LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                                        LocalDate prev = current.minusMonths(1);

                                        return transactionMethodByMerchantRepository
                                                        .findMonthlyTransactionMethodsFailedByMerchant(
                                                                        req.getMerchantId().longValue(), req.getYear(),
                                                                        req.getMonth(), prev.getYear(),
                                                                        prev.getMonthValue())
                                                        .chain(rawData -> {
                                                                List<TransactionMonthlyMethodResponse> responseList = rawData
                                                                                .stream()
                                                                                .map(TransactionMonthlyMethodResponse::from)
                                                                                .collect(Collectors.toList());

                                                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = ApiResponse
                                                                                .success(
                                                                                                "Monthly failed transaction by method & merchant retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_monthly_method_by_merchant_failed",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding monthly failed transaction by method & merchant",
                                                                                e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_monthly_method_by_merchant_failed",
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
                                                                                "find_monthly_method_by_merchant_failed"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodByMerchantSuccess(
                        YearMethodTransactionMerchantRequest req) {
                if (req.getMerchantId() == null || req.getYear() == null) {
                        return Uni.createFrom().item(ApiResponse.error("Merchant ID and Year must not be null", null));
                }

                String cacheKey = String.format("transaction:method:merchant:yearly:success:%d:%d", req.getMerchantId(),
                                req.getYear());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<TransactionYearlyMethodResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionYearlyMethodResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findYearlyMethodByMerchantSuccess")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "transaction-stats-service")
                                                        .setAttribute("operation",
                                                                        "find_yearly_method_by_merchant_success")
                                                        .setAttribute("merchant.id", req.getMerchantId().toString())
                                                        .setAttribute("year", req.getYear().toString())
                                                        .startSpan();

                                        return transactionMethodByMerchantRepository
                                                        .findYearlyTransactionMethodsSuccessByMerchant(
                                                                        req.getMerchantId().longValue(), req.getYear())
                                                        .chain(rawData -> {
                                                                List<TransactionYearlyMethodResponse> responseList = rawData
                                                                                .stream()
                                                                                .map(TransactionYearlyMethodResponse::from)
                                                                                .collect(Collectors.toList());

                                                                ApiResponse<List<TransactionYearlyMethodResponse>> response = ApiResponse
                                                                                .success(
                                                                                                "Yearly success transaction by method & merchant retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_yearly_method_by_merchant_success",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding yearly success transaction by method & merchant",
                                                                                e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_yearly_method_by_merchant_success",
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
                                                                                "find_yearly_method_by_merchant_success"));
                                                        });
                                });
        }

        @Override
        public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodByMerchantFailed(
                        YearMethodTransactionMerchantRequest req) {
                if (req.getMerchantId() == null || req.getYear() == null) {
                        return Uni.createFrom().item(ApiResponse.error("Merchant ID and Year must not be null", null));
                }

                String cacheKey = String.format("transaction:method:merchant:yearly:failed:%d:%d", req.getMerchantId(),
                                req.getYear());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<TransactionYearlyMethodResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionYearlyMethodResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findYearlyMethodByMerchantFailed")
                                                        .setSpanKind(SpanKind.SERVER)
                                                        .setAttribute("service.name", "transaction-stats-service")
                                                        .setAttribute("operation",
                                                                        "find_yearly_method_by_merchant_failed")
                                                        .setAttribute("merchant.id", req.getMerchantId().toString())
                                                        .setAttribute("year", req.getYear().toString())
                                                        .startSpan();

                                        return transactionMethodByMerchantRepository
                                                        .findYearlyTransactionMethodsFailedByMerchant(
                                                                        req.getMerchantId().longValue(), req.getYear())
                                                        .chain(rawData -> {
                                                                List<TransactionYearlyMethodResponse> responseList = rawData
                                                                                .stream()
                                                                                .map(TransactionYearlyMethodResponse::from)
                                                                                .collect(Collectors.toList());

                                                                ApiResponse<List<TransactionYearlyMethodResponse>> response = ApiResponse
                                                                                .success(
                                                                                                "Yearly failed transaction by method & merchant retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Cached response for key: {}",
                                                                                                        cacheKey);
                                                                                        span.setStatus(StatusCode.OK);

                                                                                        requestsTotal.add(1,
                                                                                                        Attributes.of(
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "operation"),
                                                                                                                        "find_yearly_method_by_merchant_failed",
                                                                                                                        AttributeKey.stringKey(
                                                                                                                                        "status"),
                                                                                                                        "success"));
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().invoke(e -> {
                                                                logger.error("Error finding yearly failed transaction by method & merchant",
                                                                                e);
                                                                span.recordException(e);
                                                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "find_yearly_method_by_merchant_failed",
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
                                                                                "find_yearly_method_by_merchant_failed"));
                                                        });
                                });
        }
}
