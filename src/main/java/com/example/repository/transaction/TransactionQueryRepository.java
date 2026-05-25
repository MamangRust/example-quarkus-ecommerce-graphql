package com.example.repository.transaction;

import java.util.Optional;

import com.example.domain.response.api.PagedResult;
import com.example.entity.transaction.Transaction;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TransactionQueryRepository implements PanacheRepository<Transaction> {

    public Uni<PagedResult<Transaction>> findTransactions(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(paymentMethod) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(status) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;
        var panacheQuery = find(query, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Transaction>> findActiveTransactions(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(paymentMethod) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(status) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;
        var panacheQuery = find(query, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Transaction>> findTrashedTransactions(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NOT NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(paymentMethod) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(status) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                """;
        var panacheQuery = find(query, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Transaction>> findTransactionsByMerchant(String keyword, Integer merchantId, int page,
            int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR LOWER(paymentMethod) LIKE LOWER(CONCAT('%', ?1, '%'))
                        OR LOWER(status) LIKE LOWER(CONCAT('%', ?1, '%'))
                    )
                    AND (?2 IS NULL OR merchantId = ?2)
                """;
        var panacheQuery = find(query, keyword, merchantId).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<Optional<Transaction>> findTransactionById(Long transactionId) {
        return find("id = ?1 AND deletedAt IS NULL", transactionId).firstResult().map(Optional::ofNullable);
    }

    public Uni<Optional<Transaction>> findByOrderId(Integer orderId) {
        return find("orderId = ?1 AND deletedAt IS NULL", orderId).firstResult().map(Optional::ofNullable);
    }
}