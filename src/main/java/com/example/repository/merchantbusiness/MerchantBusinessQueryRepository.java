package com.example.repository.merchantbusiness;

import com.example.domain.response.api.PagedResult;
import com.example.entity.merchant.MerchantBusinessInformation;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MerchantBusinessQueryRepository implements PanacheRepository<MerchantBusinessInformation> {

    public Uni<PagedResult<MerchantBusinessInformation>> findMerchantBusinessInformation(String keyword, int page,
            int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(businessType) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(taxId) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(websiteUrl) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<MerchantBusinessInformation>> findActiveMerchantBusinessInformation(String keyword, int page,
            int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(businessType) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(taxId) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(websiteUrl) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<MerchantBusinessInformation>> findTrashedMerchantBusinessInformation(String keyword,
            int page, int size) {
        var query = """
                    deletedAt IS NOT NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(businessType) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(taxId) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(websiteUrl) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<MerchantBusinessInformation> findMerchantBusinessInformationById(Long merchantBusinessInfoId) {
        return find("id = ?1 AND deletedAt IS NULL", merchantBusinessInfoId).firstResult();
    }
}
