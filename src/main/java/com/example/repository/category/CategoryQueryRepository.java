package com.example.repository.category;

import com.example.domain.response.api.PagedResult;
import com.example.entity.category.Category;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CategoryQueryRepository implements PanacheRepository<Category> {

    public Uni<PagedResult<Category>> findCategories(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(name) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(slugCategory) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Category>> findActiveCategories(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(name) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(slugCategory) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Category>> findTrashedCategories(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NOT NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(name) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(description) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(slugCategory) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;

        var panacheQuery = find(query, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<Category> findCategoryById(Long categoryId) {
        return find("id = ?1 AND deletedAt IS NULL", categoryId).firstResult();
    }
}