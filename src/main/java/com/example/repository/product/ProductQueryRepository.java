package com.example.repository.product;

import java.util.Optional;
import java.util.List;
import com.example.domain.response.api.PagedResult;
import com.example.entity.Product;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductQueryRepository implements PanacheRepository<Product> {

    public Uni<PagedResult<Product>> findProducts(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(name) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(slugProduct) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;
        var panacheQuery = find(query, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Product>> findActiveProducts(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(name) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(slugProduct) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;
        var panacheQuery = find(query, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Product>> findTrashedProducts(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NOT NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(name) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(slugProduct) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;
        var panacheQuery = find(query, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Product>> findByMerchant(Integer merchantId, String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND merchantId = ?1
                    AND (
                        ?2 IS NULL
                        OR LOWER(name) LIKE LOWER(CONCAT('%', ?2, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?2, '%'))
                        OR LOWER(slugProduct) LIKE LOWER(CONCAT('%', ?2, '%'))
                    )
                """;
        var panacheQuery = find(query, merchantId, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Product>> findByCategory(Integer categoryId, String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND categoryId = ?1
                    AND (
                        ?2 IS NULL
                        OR LOWER(name) LIKE LOWER(CONCAT('%', ?2, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?2, '%'))
                        OR LOWER(slugProduct) LIKE LOWER(CONCAT('%', ?2, '%'))
                    )
                """;
        var panacheQuery = find(query, categoryId, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<Optional<Product>> findProductById(Long productId) {
        return find("id = ?1 AND deletedAt IS NULL", productId).firstResult().map(Optional::ofNullable);
    }

    public Uni<PagedResult<Product>> findProductsByMerchantNative(
            Integer merchantId, String keyword, Integer categoryId, Integer minPrice, Integer maxPrice, int page,
            int size) {

        String baseSql = """
                FROM products p
                JOIN categories c ON p.category_id = c.id
                WHERE p.deleted_at IS NULL
                  AND p.merchant_id = :merchantId
                  AND (
                      :keyword IS NULL OR :keyword = '' OR
                      p.name ILIKE CONCAT('%', :keyword, '%') OR
                      p.description ILIKE CONCAT('%', :keyword, '%') OR
                      p.slug_product ILIKE CONCAT('%', :keyword, '%')
                  )
                  AND (
                      (:categoryId IS NULL OR :categoryId = 0) OR c.id = :categoryId
                  )
                  AND (
                      p.price >= COALESCE(:minPrice, 0)
                      AND p.price <= COALESCE(:maxPrice, 999999999)
                  )
                """;

        String countSql = "SELECT COUNT(*) " + baseSql;
        String dataSql = "SELECT p.* " + baseSql + " ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset";

        return Panache.getSession().chain(session -> {
            var countQuery = session.createNativeQuery(countSql);
            countQuery.setParameter("merchantId", merchantId);
            countQuery.setParameter("keyword", keyword);
            countQuery.setParameter("categoryId", categoryId);
            countQuery.setParameter("minPrice", minPrice);
            countQuery.setParameter("maxPrice", maxPrice);

            return countQuery.getSingleResult().chain(countVal -> {
                long total = ((Number) countVal).longValue();

                var dataQuery = session.createNativeQuery(dataSql, Product.class);
                dataQuery.setParameter("merchantId", merchantId);
                dataQuery.setParameter("keyword", keyword);
                dataQuery.setParameter("categoryId", categoryId);
                dataQuery.setParameter("minPrice", minPrice);
                dataQuery.setParameter("maxPrice", maxPrice);
                dataQuery.setParameter("limit", size);
                dataQuery.setParameter("offset", page * size);

                return dataQuery.getResultList().map(list -> new PagedResult<>((List<Product>) list, (int) total));
            });
        });
    }

    public Uni<PagedResult<Product>> findProductsByCategoryNameNative(
            String categoryName, String keyword, Integer minPrice, Integer maxPrice, int page, int size) {

        String baseSql = """
                FROM products p
                JOIN categories c ON p.category_id = c.id
                WHERE p.deleted_at IS NULL
                  AND c.name = :categoryName
                  AND (
                      :keyword IS NULL OR :keyword = '' OR
                      p.name ILIKE CONCAT('%', :keyword, '%') OR
                      p.description ILIKE CONCAT('%', :keyword, '%') OR
                      p.slug_product ILIKE CONCAT('%', :keyword, '%')
                  )
                  AND (
                      p.price >= COALESCE(:minPrice, 0)
                      AND p.price <= COALESCE(:maxPrice, 999999999)
                  )
                """;

        String countSql = "SELECT COUNT(*) " + baseSql;
        String dataSql = "SELECT p.* " + baseSql + " ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset";

        return Panache.getSession().chain(session -> {
            var countQuery = session.createNativeQuery(countSql);
            countQuery.setParameter("categoryName", categoryName);
            countQuery.setParameter("keyword", keyword);
            countQuery.setParameter("minPrice", minPrice);
            countQuery.setParameter("maxPrice", maxPrice);

            return countQuery.getSingleResult().chain(countVal -> {
                long total = ((Number) countVal).longValue();

                var dataQuery = session.createNativeQuery(dataSql, Product.class);
                dataQuery.setParameter("categoryName", categoryName);
                dataQuery.setParameter("keyword", keyword);
                dataQuery.setParameter("minPrice", minPrice);
                dataQuery.setParameter("maxPrice", maxPrice);
                dataQuery.setParameter("limit", size);
                dataQuery.setParameter("offset", page * size);

                return dataQuery.getResultList().map(list -> new PagedResult<>((List<Product>) list, (int) total));
            });
        });
    }
}
