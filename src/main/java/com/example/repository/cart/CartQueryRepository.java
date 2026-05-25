package com.example.repository.cart;

import com.example.domain.response.api.PagedResult;
import com.example.entity.Cart;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CartQueryRepository implements PanacheRepository<Cart> {

    public Uni<PagedResult<Cart>> findCartsByUser(Integer userId, String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND userId = ?1
                    AND (
                        ?2 IS NULL
                        OR LOWER(name) LIKE LOWER(CONCAT('%', ?2, '%'))
                        OR CAST(price AS string) LIKE CONCAT('%', ?2, '%')
                    )
                """;

        var panacheQuery = find(query, userId, keyword)
                .page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }
}
