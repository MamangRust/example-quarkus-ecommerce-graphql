package com.example.service.impl.shippingaddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.RedisService;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.shipping.ShippingAddressResponseDeleteAt;
import com.example.exception.ResourceNotFoundException;
import com.example.repository.shippingaddress.ShippingAddressCommandRepository;
import com.example.service.shippingaddress.ShippingAddressCommand;

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
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ShippingAddressCommandImplService implements ShippingAddressCommand {
        private static final Logger logger = LoggerFactory.getLogger(ShippingAddressCommandImplService.class);

        ShippingAddressCommandRepository shippingAddressCommandRepository;
        OpenTelemetry openTelemetry;
        RedisService redisService;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        @Inject
        public ShippingAddressCommandImplService(ShippingAddressCommandRepository shippingAddressCommandRepository,
                        OpenTelemetry openTelemetry,
                        RedisService redisService) {
                this.shippingAddressCommandRepository = shippingAddressCommandRepository;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.tracer = openTelemetry.getTracer("shipping-address-command-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("shipping-address-command-service");

                this.requestsTotal = meter.counterBuilder("requests_total")
                                .setDescription("Total number of requests")
                                .build();
                this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                                .setDescription("Request duration in seconds")
                                .setUnit("s")
                                .build();
        }

        private Uni<Void> invalidateCache(Long shippingId) {
                if (shippingId != null) {
                        return redisService.deleteReactive("shipping:id:" + shippingId).replaceWithVoid();
                }
                return Uni.createFrom().voidItem();
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<ShippingAddressResponseDeleteAt>> trash(Integer shippingId) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("trashShippingAddress")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "shipping-address-service")
                                .setAttribute("operation", "trash_shipping_address")
                                .setAttribute("shipping.id", shippingId.toString())
                                .startSpan();

                logger.info("🗑️ Trashing shipping address id={}", shippingId);

                return shippingAddressCommandRepository.trashed(shippingId.longValue())
                                .chain(address -> {
                                        if (address == null) {
                                                throw new ResourceNotFoundException(
                                                                "Shipping address not found or already trashed");
                                        }
                                        ShippingAddressResponseDeleteAt response = ShippingAddressResponseDeleteAt
                                                        .from(address);

                                        return invalidateCache(shippingId.longValue())
                                                        .map(v -> {
                                                                logger.info("Successfully trashed shipping address with ID: {}",
                                                                                shippingId);
                                                                span.setStatus(StatusCode.OK);

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "trash_shipping_address",
                                                                                AttributeKey.stringKey("status"),
                                                                                "success"));

                                                                return ApiResponse.success(
                                                                                "🗑️ Shipping address trashed successfully!",
                                                                                response);
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("💥 Failed to trash shipping address id={}", shippingId, e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "trash_shipping_address",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "trash_shipping_address"));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<ShippingAddressResponseDeleteAt>> restore(Integer shippingId) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("restoreShippingAddress")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "shipping-address-service")
                                .setAttribute("operation", "restore_shipping_address")
                                .setAttribute("shipping.id", shippingId.toString())
                                .startSpan();

                logger.info("♻️ Restoring shipping address id={}", shippingId);

                return shippingAddressCommandRepository.restore(shippingId.longValue())
                                .chain(address -> {
                                        if (address == null) {
                                                throw new ResourceNotFoundException(
                                                                "Shipping address not found or not trashed");
                                        }
                                        ShippingAddressResponseDeleteAt response = ShippingAddressResponseDeleteAt
                                                        .from(address);

                                        return invalidateCache(shippingId.longValue())
                                                        .map(v -> {
                                                                logger.info("Successfully restored shipping address with ID: {}",
                                                                                shippingId);
                                                                span.setStatus(StatusCode.OK);

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "restore_shipping_address",
                                                                                AttributeKey.stringKey("status"),
                                                                                "success"));

                                                                return ApiResponse.success(
                                                                                "♻️ Shipping address restored successfully!",
                                                                                response);
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("💥 Failed to restore shipping address id={}", shippingId, e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_shipping_address",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "restore_shipping_address"));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Boolean>> deletePermanently(Integer shippingId) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("deleteShippingAddressPermanent")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "shipping-address-service")
                                .setAttribute("operation", "delete_shipping_address_permanent")
                                .setAttribute("shipping.id", shippingId.toString())
                                .startSpan();

                logger.warn("🧨 Permanently deleting shipping address id={}", shippingId);

                return shippingAddressCommandRepository.deletePermanent(shippingId.longValue())
                                .chain(deleted -> {
                                        return invalidateCache(shippingId.longValue())
                                                        .map(v -> {
                                                                logger.info("Successfully permanently deleted shipping address with ID: {}",
                                                                                shippingId);
                                                                span.setStatus(StatusCode.OK);

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"),
                                                                                "delete_shipping_address_permanent",
                                                                                AttributeKey.stringKey("status"),
                                                                                "success"));

                                                                return ApiResponse.success(
                                                                                "🧨 Shipping address permanently deleted!",
                                                                                deleted);
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("💥 Failed to permanently delete shipping address id={}",
                                                        shippingId, e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_shipping_address_permanent",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_shipping_address_permanent"));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Boolean>> restoreAll() {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("restoreAllShippingAddresses")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "shipping-address-service")
                                .setAttribute("operation", "restore_all_shipping_addresses")
                                .startSpan();

                logger.info("🔄 Restoring ALL trashed shipping addresses");

                return shippingAddressCommandRepository.restoreAllDeleted()
                                .map(restored -> {
                                        logger.info("Successfully restored all trashed shipping addresses");
                                        span.setStatus(StatusCode.OK);

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "restore_all_shipping_addresses",
                                                        AttributeKey.stringKey("status"), "success"));

                                        return ApiResponse.success("🔄 All shipping addresses restored successfully!",
                                                        restored);
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("💥 Failed to restore all shipping addresses", e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "restore_all_shipping_addresses",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "restore_all_shipping_addresses"));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Boolean>> deleteAllPermanent() {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("deleteAllShippingAddressesPermanent")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "shipping-address-service")
                                .setAttribute("operation", "delete_all_shipping_addresses_permanent")
                                .startSpan();

                logger.warn("💣 Permanently deleting ALL trashed shipping addresses");

                return shippingAddressCommandRepository.deleteAllDeleted()
                                .map(deleted -> {
                                        logger.info("Successfully permanently deleted all trashed shipping addresses");
                                        span.setStatus(StatusCode.OK);

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_all_shipping_addresses_permanent",
                                                        AttributeKey.stringKey("status"), "success"));

                                        return ApiResponse.success("💣 All shipping addresses permanently deleted!",
                                                        deleted);
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("💥 Failed to delete all shipping addresses", e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_all_shipping_addresses_permanent",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"),
                                                        e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"),
                                                        "delete_all_shipping_addresses_permanent"));
                                });
        }
}
