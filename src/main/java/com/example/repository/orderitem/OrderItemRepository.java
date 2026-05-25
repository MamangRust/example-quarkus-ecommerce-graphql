package com.example.repository.orderitem;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import com.example.entity.OrderItem;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderItemRepository implements PanacheRepository<OrderItem> {

    public Uni<List<OrderItem>> findOrderItemByOrder(Long orderId) {
        return list("orderId = ?1 AND deletedAt IS NULL", orderId.intValue());
    }

    @WithTransaction
    public Uni<OrderItem> trashed(Long orderItemId) {
        return findById(orderItemId)
                .chain(item -> {
                    if (item != null && item.getDeletedAt() == null) {
                        LocalDateTime date = LocalDateTime.now();
                        item.setDeletedAt(Timestamp.valueOf(date));
                        return persist(item).map(v -> item);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    @WithTransaction
    public Uni<OrderItem> restore(Long orderItemId) {
        return find("id = ?1 AND deletedAt IS NOT NULL", orderItemId).firstResult()
                .chain(item -> {
                    if (item != null) {
                        item.setDeletedAt(null);
                        return persist(item).map(v -> item);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    @WithTransaction
    public Uni<Boolean> deletePermanent(Long orderItemId) {
        return find("id = ?1 AND deletedAt IS NOT NULL", orderItemId).firstResult()
                .chain(item -> {
                    if (item != null) {
                        return delete(item).map(v -> true);
                    }
                    return Uni.createFrom().item(false);
                });
    }

    @WithTransaction
    public Uni<Boolean> restoreAllDeleted() {
        return update("deletedAt = NULL WHERE deletedAt IS NOT NULL")
                .map(count -> count > 0);
    }

    @WithTransaction
    public Uni<Boolean> deleteAllDeleted() {
        return delete("deletedAt IS NOT NULL")
                .map(count -> count > 0);
    }
}
