package com.example.repository.shippingaddress;

import com.example.domain.response.api.PagedResult;
import com.example.entity.ShippingAddress;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class ShippingAddressQueryRepository implements PanacheRepository<ShippingAddress> {

    public Uni<PagedResult<ShippingAddress>> findShippingAddresses(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(kota) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(provinsi) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(negara) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<ShippingAddress>> findActiveShippingAddresses(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(kota) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(provinsi) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(negara) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<ShippingAddress>> findTrashedShippingAddresses(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NOT NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(kota) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(provinsi) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(negara) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<Optional<ShippingAddress>> findByIdNative(Long id) {
        return find("id = ?1 AND deletedAt IS NULL", id).firstResult().map(Optional::ofNullable);
    }

    public Uni<Optional<ShippingAddress>> findByOrderId(Integer orderId) {
        return find("orderId = ?1 AND deletedAt IS NULL", orderId).firstResult().map(Optional::ofNullable);
    }
}
