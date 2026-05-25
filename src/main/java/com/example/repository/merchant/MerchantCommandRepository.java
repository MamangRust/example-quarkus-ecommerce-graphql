package com.example.repository.merchant;

import com.example.entity.merchant.Merchant;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@ApplicationScoped
public class MerchantCommandRepository implements PanacheRepository<Merchant> {

    @WithTransaction
    public Uni<Merchant> trashed(Long merchantId) {
        return findById(merchantId)
                .chain(merchant -> {
                    if (merchant != null && merchant.getDeletedAt() == null) {
                        LocalDateTime date = LocalDateTime.now();
                        merchant.setDeletedAt(Timestamp.valueOf(date));
                        return persist(merchant).map(v -> merchant);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    @WithTransaction
    public Uni<Merchant> restore(Long merchantId) {
        return find("id = ?1 AND deletedAt IS NOT NULL", merchantId).firstResult()
                .chain(merchant -> {
                    if (merchant != null) {
                        merchant.setDeletedAt(null);
                        return persist(merchant).map(v -> merchant);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    @WithTransaction
    public Uni<Boolean> deletePermanent(Long merchantId) {
        return find("id = ?1 AND deletedAt IS NOT NULL", merchantId).firstResult()
                .chain(merchant -> {
                    if (merchant != null) {
                        return delete(merchant).map(v -> true);
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
