package com.example.repository.transaction.statsbymerchant;

import java.util.ArrayList;
import java.util.List;

import com.example.entity.transaction.Transaction;
import com.example.entity.transaction.TransactionMonthlyMethod;
import com.example.entity.transaction.TransactionYearlyMethod;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TransactionMethodByMerchantRepository implements PanacheRepository<Transaction> {

    public Uni<List<TransactionMonthlyMethod>> findMonthlyTransactionMethodsSuccessByMerchant(
            Long merchantId,
            Integer year1,
            Integer month1,
            Integer year2,
            Integer month2) {

        String sql = """
                WITH
                    date_ranges AS (
                        SELECT
                            PARSEDATETIME(CAST(:year1 AS VARCHAR) || '-' || LPAD(CAST(:month1 AS VARCHAR), 2, '0') || '-01', 'yyyy-MM-dd') AS range1_start,
                            DATEADD('MONTH', 1, PARSEDATETIME(CAST(:year1 AS VARCHAR) || '-' || LPAD(CAST(:month1 AS VARCHAR), 2, '0') || '-01', 'yyyy-MM-dd')) AS range1_end,
                            PARSEDATETIME(CAST(:year2 AS VARCHAR) || '-' || LPAD(CAST(:month2 AS VARCHAR), 2, '0') || '-01', 'yyyy-MM-dd') AS range2_start,
                            DATEADD('MONTH', 1, PARSEDATETIME(CAST(:year2 AS VARCHAR) || '-' || LPAD(CAST(:month2 AS VARCHAR), 2, '0') || '-01', 'yyyy-MM-dd')) AS range2_end
                    ),
                    payment_methods AS (
                        SELECT DISTINCT payment_method
                        FROM transactions
                        WHERE deleted_at IS NULL
                          AND merchant_id = :merchantId
                    ),
                    all_months AS (
                        SELECT range1_start AS activity_month FROM date_ranges
                        UNION
                        SELECT range2_start FROM date_ranges
                    ),
                    all_combinations AS (
                        SELECT am.activity_month, pm.payment_method
                        FROM all_months am
                        CROSS JOIN payment_methods pm
                    ),
                    monthly_transactions AS (
                        SELECT
                            PARSEDATETIME(FORMATDATETIME(t.created_at, 'yyyy-MM-01'), 'yyyy-MM-dd') AS activity_month,
                            t.payment_method,
                            COUNT(t.id) AS total_transactions,
                            CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                        FROM transactions t
                        JOIN date_ranges dr ON (
                            (t.created_at >= dr.range1_start AND t.created_at < dr.range1_end)
                            OR
                            (t.created_at >= dr.range2_start AND t.created_at < dr.range2_end)
                        )
                        WHERE t.deleted_at IS NULL
                          AND t.payment_status = 'SUCCESS'
                          AND t.merchant_id = :merchantId
                        GROUP BY PARSEDATETIME(FORMATDATETIME(t.created_at, 'yyyy-MM-01'), 'yyyy-MM-dd'), t.payment_method
                    )
                SELECT
                    FORMATDATETIME(ac.activity_month, 'MMM') AS "month",
                    ac.payment_method AS paymentMethod,
                    CAST(COALESCE(mt.total_transactions, 0) AS BIGINT) AS totalTransactions,
                    CAST(COALESCE(mt.total_amount, 0) AS BIGINT) AS totalAmount
                FROM all_combinations ac
                LEFT JOIN monthly_transactions mt
                    ON ac.activity_month = mt.activity_month
                    AND ac.payment_method = mt.payment_method
                ORDER BY ac.activity_month, ac.payment_method
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("merchantId", merchantId);
            dataQuery.setParameter("year1", year1);
            dataQuery.setParameter("month1", month1);
            dataQuery.setParameter("year2", year2);
            dataQuery.setParameter("month2", month2);

            return dataQuery.getResultList().map(results -> {
                List<TransactionMonthlyMethod> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    TransactionMonthlyMethod obj = new TransactionMonthlyMethod();
                    obj.setMonth((String) row[0]);
                    obj.setPaymentMethod((String) row[1]);
                    obj.setTotalTransactions(((Number) row[2]).longValue());
                    obj.setTotalAmount(((Number) row[3]).longValue());
                    list.add(obj);
                }
                return list;
            });
        });
    }

    public Uni<List<TransactionMonthlyMethod>> findMonthlyTransactionMethodsFailedByMerchant(
            Long merchantId,
            Integer year1,
            Integer month1,
            Integer year2,
            Integer month2) {

        String sql = """
                WITH
                    date_ranges AS (
                        SELECT
                            PARSEDATETIME(CAST(:year1 AS VARCHAR) || '-' || LPAD(CAST(:month1 AS VARCHAR), 2, '0') || '-01', 'yyyy-MM-dd') AS range1_start,
                            DATEADD('MONTH', 1, PARSEDATETIME(CAST(:year1 AS VARCHAR) || '-' || LPAD(CAST(:month1 AS VARCHAR), 2, '0') || '-01', 'yyyy-MM-dd')) AS range1_end,
                            PARSEDATETIME(CAST(:year2 AS VARCHAR) || '-' || LPAD(CAST(:month2 AS VARCHAR), 2, '0') || '-01', 'yyyy-MM-dd') AS range2_start,
                            DATEADD('MONTH', 1, PARSEDATETIME(CAST(:year2 AS VARCHAR) || '-' || LPAD(CAST(:month2 AS VARCHAR), 2, '0') || '-01', 'yyyy-MM-dd')) AS range2_end
                    ),
                    payment_methods AS (
                        SELECT DISTINCT payment_method
                        FROM transactions
                        WHERE deleted_at IS NULL
                          AND merchant_id = :merchantId
                    ),
                    all_months AS (
                        SELECT range1_start AS activity_month FROM date_ranges
                        UNION
                        SELECT range2_start FROM date_ranges
                    ),
                    all_combinations AS (
                        SELECT am.activity_month, pm.payment_method
                        FROM all_months am
                        CROSS JOIN payment_methods pm
                    ),
                    monthly_transactions AS (
                        SELECT
                            PARSEDATETIME(FORMATDATETIME(t.created_at, 'yyyy-MM-01'), 'yyyy-MM-dd') AS activity_month,
                            t.payment_method,
                            COUNT(t.id) AS total_transactions,
                            CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                        FROM transactions t
                        JOIN date_ranges dr ON (
                            (t.created_at >= dr.range1_start AND t.created_at < dr.range1_end)
                            OR
                            (t.created_at >= dr.range2_start AND t.created_at < dr.range2_end)
                        )
                        WHERE t.deleted_at IS NULL
                          AND t.payment_status = 'FAILED'
                          AND t.merchant_id = :merchantId
                        GROUP BY PARSEDATETIME(FORMATDATETIME(t.created_at, 'yyyy-MM-01'), 'yyyy-MM-dd'), t.payment_method
                    )
                SELECT
                    FORMATDATETIME(ac.activity_month, 'MMM') AS "month",
                    ac.payment_method AS paymentMethod,
                    CAST(COALESCE(mt.total_transactions, 0) AS BIGINT) AS totalTransactions,
                    CAST(COALESCE(mt.total_amount, 0) AS BIGINT) AS totalAmount
                FROM all_combinations ac
                LEFT JOIN monthly_transactions mt
                    ON ac.activity_month = mt.activity_month
                    AND ac.payment_method = mt.payment_method
                ORDER BY ac.activity_month, ac.payment_method
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("merchantId", merchantId);
            dataQuery.setParameter("year1", year1);
            dataQuery.setParameter("month1", month1);
            dataQuery.setParameter("year2", year2);
            dataQuery.setParameter("month2", month2);

            return dataQuery.getResultList().map(results -> {
                List<TransactionMonthlyMethod> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    TransactionMonthlyMethod obj = new TransactionMonthlyMethod();
                    obj.setMonth((String) row[0]);
                    obj.setPaymentMethod((String) row[1]);
                    obj.setTotalTransactions(((Number) row[2]).longValue());
                    obj.setTotalAmount(((Number) row[3]).longValue());
                    list.add(obj);
                }
                return list;
            });
        });
    }

    public Uni<List<TransactionYearlyMethod>> findYearlyTransactionMethodsSuccessByMerchant(
            Long merchantId,
            Integer year) {

        String sql = """
                WITH
                    year_range AS (
                        SELECT
                            CAST(:year AS INTEGER) - 1 AS start_year,
                            CAST(:year AS INTEGER) AS end_year
                    ),
                    payment_methods AS (
                        SELECT DISTINCT payment_method
                        FROM transactions
                        WHERE deleted_at IS NULL
                          AND merchant_id = :merchantId
                    ),
                    all_years AS (
                        SELECT (SELECT start_year FROM year_range) AS "year"
                        UNION
                        SELECT (SELECT end_year FROM year_range) AS "year"
                    ),
                    all_combinations AS (
                        SELECT CAST(ay."year" AS VARCHAR) AS "year", pm.payment_method
                        FROM all_years ay
                        CROSS JOIN payment_methods pm
                    ),
                    yearly_transactions AS (
                        SELECT
                            CAST(EXTRACT(YEAR FROM t.created_at) AS VARCHAR) AS "year",
                            t.payment_method,
                            COUNT(t.id) AS total_transactions,
                            CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                        FROM transactions t
                        WHERE
                            t.deleted_at IS NULL
                            AND t.payment_status = 'SUCCESS'
                            AND t.merchant_id = :merchantId
                            AND EXTRACT(YEAR FROM t.created_at) BETWEEN (SELECT start_year FROM year_range) AND (SELECT end_year FROM year_range)
                        GROUP BY CAST(EXTRACT(YEAR FROM t.created_at) AS VARCHAR), t.payment_method
                    )
                SELECT
                    ac."year" AS "year",
                    ac.payment_method AS paymentMethod,
                    CAST(COALESCE(yt.total_transactions, 0) AS BIGINT) AS totalTransactions,
                    CAST(COALESCE(yt.total_amount, 0) AS BIGINT) AS totalAmount
                FROM all_combinations ac
                LEFT JOIN yearly_transactions yt
                    ON ac."year" = yt."year"
                    AND ac.payment_method = yt.payment_method
                ORDER BY ac."year", ac.payment_method
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("merchantId", merchantId);
            dataQuery.setParameter("year", year);

            return dataQuery.getResultList().map(results -> {
                List<TransactionYearlyMethod> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    TransactionYearlyMethod obj = new TransactionYearlyMethod();
                    obj.setYear((String) row[0]);
                    obj.setPaymentMethod((String) row[1]);
                    obj.setTotalTransactions(((Number) row[2]).longValue());
                    obj.setTotalAmount(((Number) row[3]).longValue());
                    list.add(obj);
                }
                return list;
            });
        });
    }

    public Uni<List<TransactionYearlyMethod>> findYearlyTransactionMethodsFailedByMerchant(
            Long merchantId,
            Integer year) {

        String sql = """
                WITH
                    year_range AS (
                        SELECT
                            CAST(:year AS INTEGER) - 1 AS start_year,
                            CAST(:year AS INTEGER) AS end_year
                    ),
                    payment_methods AS (
                        SELECT DISTINCT payment_method
                        FROM transactions
                        WHERE deleted_at IS NULL
                          AND merchant_id = :merchantId
                    ),
                    all_years AS (
                        SELECT (SELECT start_year FROM year_range) AS "year"
                        UNION
                        SELECT (SELECT end_year FROM year_range) AS "year"
                    ),
                    all_combinations AS (
                        SELECT CAST(ay."year" AS VARCHAR) AS "year", pm.payment_method
                        FROM all_years ay
                        CROSS JOIN payment_methods pm
                    ),
                    yearly_transactions AS (
                        SELECT
                            CAST(EXTRACT(YEAR FROM t.created_at) AS VARCHAR) AS "year",
                            t.payment_method,
                            COUNT(t.id) AS total_transactions,
                            CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                        FROM transactions t
                        WHERE
                            t.deleted_at IS NULL
                            AND t.payment_status = 'FAILED'
                            AND t.merchant_id = :merchantId
                            AND EXTRACT(YEAR FROM t.created_at) BETWEEN (SELECT start_year FROM year_range) AND (SELECT end_year FROM year_range)
                        GROUP BY CAST(EXTRACT(YEAR FROM t.created_at) AS VARCHAR), t.payment_method
                    )
                SELECT
                    ac."year" AS "year",
                    ac.payment_method AS paymentMethod,
                    CAST(COALESCE(yt.total_transactions, 0) AS BIGINT) AS totalTransactions,
                    CAST(COALESCE(yt.total_amount, 0) AS BIGINT) AS totalAmount
                FROM all_combinations ac
                LEFT JOIN yearly_transactions yt
                    ON ac."year" = yt."year"
                    AND ac.payment_method = yt.payment_method
                ORDER BY ac."year", ac.payment_method
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("merchantId", merchantId);
            dataQuery.setParameter("year", year);

            return dataQuery.getResultList().map(results -> {
                List<TransactionYearlyMethod> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    TransactionYearlyMethod obj = new TransactionYearlyMethod();
                    obj.setYear((String) row[0]);
                    obj.setPaymentMethod((String) row[1]);
                    obj.setTotalTransactions(((Number) row[2]).longValue());
                    obj.setTotalAmount(((Number) row[3]).longValue());
                    list.add(obj);
                }
                return list;
            });
        });
    }
}