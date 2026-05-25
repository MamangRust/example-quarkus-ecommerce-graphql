package com.example.repository.review_detail;

import com.example.entity.review.ReviewDetail;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@ApplicationScoped
public class ReviewDetailRepository implements PanacheRepository<ReviewDetail> {

    @WithTransaction
    public Uni<ReviewDetail> trashed(Long reviewDetailId) {
        return findById(reviewDetailId)
                .chain(detail -> {
                    if (detail != null && detail.getDeletedAt() == null) {
                        LocalDateTime date = LocalDateTime.now();
                        detail.setDeletedAt(Timestamp.valueOf(date));
                        return persist(detail).map(v -> detail);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    @WithTransaction
    public Uni<ReviewDetail> restore(Long reviewDetailId) {
        return find("id = ?1 AND deletedAt IS NOT NULL", reviewDetailId).firstResult()
                .chain(detail -> {
                    if (detail != null) {
                        detail.setDeletedAt(null);
                        return persist(detail).map(v -> detail);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    @WithTransaction
    public Uni<Boolean> deletePermanent(Long reviewDetailId) {
        return find("id = ?1 AND deletedAt IS NOT NULL", reviewDetailId).firstResult()
                .chain(detail -> {
                    if (detail != null) {
                        return delete(detail).map(v -> true);
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
