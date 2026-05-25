package com.example.repository.product;

import com.example.entity.Product;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@ApplicationScoped
public class ProductCommandRepository implements PanacheRepository<Product> {

    @WithTransaction
    public Uni<Product> trashed(Long productId) {
        return findById(productId)
                .chain(product -> {
                    if (product != null && product.getDeletedAt() == null) {
                        LocalDateTime date = LocalDateTime.now();
                        product.setDeletedAt(Timestamp.valueOf(date));
                        return persist(product).map(v -> product);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    @WithTransaction
    public Uni<Product> restore(Long productId) {
        return find("id = ?1 AND deletedAt IS NOT NULL", productId).firstResult()
                .chain(product -> {
                    if (product != null) {
                        product.setDeletedAt(null);
                        return persist(product).map(v -> product);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    @WithTransaction
    public Uni<Boolean> deletePermanent(Long productId) {
        return find("id = ?1 AND deletedAt IS NOT NULL", productId).firstResult()
                .chain(product -> {
                    if (product != null) {
                        return delete(product).map(v -> true);
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