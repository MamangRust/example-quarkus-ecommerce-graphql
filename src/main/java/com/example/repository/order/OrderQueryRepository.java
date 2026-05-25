package com.example.repository.order;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.domain.response.api.PagedResult;
import com.example.entity.OrderItem;
import com.example.entity.order.Order;
import com.example.entity.order.OrderRelation;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderQueryRepository implements PanacheRepository<Order> {

    public Uni<PagedResult<Order>> findOrders(String keyword, int page, int size) {
        var query = """
                    (
                        ?1 IS NULL
                        OR CAST(id AS string) LIKE CONCAT('%', ?1, '%')
                        OR CAST(totalPrice AS string) LIKE CONCAT('%', ?1, '%')
                    )
                """;
        var panacheQuery = find(query, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Order>> findActiveOrders(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR CAST(id AS string) LIKE CONCAT('%', ?1, '%')
                        OR CAST(totalPrice AS string) LIKE CONCAT('%', ?1, '%')
                    )
                """;
        var panacheQuery = find(query, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Order>> findTrashedOrders(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NOT NULL
                    AND (
                        ?1 IS NULL
                        OR CAST(id AS string) LIKE CONCAT('%', ?1, '%')
                        OR CAST(totalPrice AS string) LIKE CONCAT('%', ?1, '%')
                    )
                """;
        var panacheQuery = find(query, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Order>> findOrdersByMerchant(String keyword, Long merchantId, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR CAST(id AS string) LIKE CONCAT('%', ?1, '%')
                        OR CAST(totalPrice AS string) LIKE CONCAT('%', ?1, '%')
                    )
                    AND (?2 IS NULL OR merchantId = ?2)
                """;
        var panacheQuery = find(query, keyword, merchantId).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<Optional<Order>> findOrderById(Long orderId) {
        return find("id = ?1 AND deletedAt IS NULL", orderId).firstResult().map(Optional::ofNullable);
    }

    public Uni<OrderRelation> findOrderRelations(Long orderId) {
        String sql = """
                    SELECT o.id, o.user_id, o.merchant_id, o.total_price,
                           oi.id, oi.product_id, oi.quantity, oi.price
                    FROM orders o
                    LEFT JOIN order_items oi ON o.id = oi.order_id AND oi.deleted_at IS NULL
                    WHERE o.id = :orderId
                      AND o.deleted_at IS NULL
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("orderId", orderId);
            return dataQuery.getResultList().map(results -> {
                if (results.isEmpty()) {
                    return null;
                }

                OrderRelation orderRelation = null;

                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    if (orderRelation == null) {
                        orderRelation = new OrderRelation();
                        orderRelation.setOrderId(((Number) row[0]).longValue());
                        orderRelation.setUserId(((Number) row[1]).intValue());
                        orderRelation.setMerchantId(((Number) row[2]).intValue());
                        orderRelation.setTotalPrice(((Number) row[3]).intValue());
                        orderRelation.setOrderItems(new ArrayList<>());
                    }

                    if (row[4] != null) {
                        OrderItem orderItem = new OrderItem();
                        orderItem.id = ((Number) row[4]).longValue();
                        orderItem.setOrderId(((Number) row[0]).intValue());
                        orderItem.setProductId(((Number) row[5]).intValue());
                        orderItem.setQuantity(((Number) row[6]).intValue());
                        orderItem.setPrice(((Number) row[7]).intValue());
                        orderRelation.getOrderItems().add(orderItem);
                    }
                }
                return orderRelation;
            });
        });
    }
}
