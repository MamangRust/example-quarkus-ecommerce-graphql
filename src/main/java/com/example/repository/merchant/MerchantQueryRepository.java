package com.example.repository.merchant;

import com.example.domain.response.api.PagedResult;
import com.example.entity.merchant.Merchant;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MerchantQueryRepository implements PanacheRepository<Merchant> {

    public Uni<PagedResult<Merchant>> findMerchants(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(name) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(address) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(contactEmail) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(contactPhone) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Merchant>> findActiveMerchants(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(name) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(address) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(contactEmail) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(contactPhone) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Merchant>> findTrashedMerchants(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NOT NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(name) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(address) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(contactEmail) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(contactPhone) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<Merchant> findMerchantById(Long merchantId) {
        return find("id = ?1 AND deletedAt IS NULL", merchantId).firstResult();
    }

    public Uni<Merchant> findMerchantByUserId(Integer userId) {
        return find("userId = ?1 AND deletedAt IS NULL", userId).firstResult();
    }
}
