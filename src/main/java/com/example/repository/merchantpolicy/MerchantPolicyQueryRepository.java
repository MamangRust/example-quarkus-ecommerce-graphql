package com.example.repository.merchantpolicy;

import com.example.domain.response.api.PagedResult;
import com.example.entity.merchant.MerchantPolicy;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MerchantPolicyQueryRepository implements PanacheRepository<MerchantPolicy> {

    public Uni<PagedResult<MerchantPolicy>> findMerchantPolicies(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(policyType) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(title) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<MerchantPolicy>> findActiveMerchantPolicies(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(policyType) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(title) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<MerchantPolicy>> findTrashedMerchantPolicies(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NOT NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(policyType) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(title) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<MerchantPolicy> findMerchantPolicyById(Long merchantPolicyId) {
        return find("id = ?1 AND deletedAt IS NULL", merchantPolicyId).firstResult();
    }
}
