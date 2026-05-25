package com.example.repository.transaction.stats;

import java.util.ArrayList;
import java.util.List;

import com.example.entity.transaction.Transaction;
import com.example.entity.transaction.TransactionMonthlyAmountFailed;
import com.example.entity.transaction.TransactionMonthlyAmountSuccess;
import com.example.entity.transaction.TransactionYearlyAmountFailed;
import com.example.entity.transaction.TransactionYearlyAmountSuccess;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TransactionAmountStatusRepository implements PanacheRepository<Transaction> {

    public Uni<List<TransactionMonthlyAmountSuccess>> findMonthlyTransactionSuccess(
            Integer year,
            Integer month,
            Integer prevYear,
            Integer prevMonth) {

        String sql = """
                WITH monthly_data AS (
                    SELECT
                        CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS "year",
                        CAST(EXTRACT(MONTH FROM t.created_at) AS INTEGER) AS "month",
                        COUNT(*) AS total_success,
                        CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                    FROM transactions t
                    WHERE
                        t.deleted_at IS NULL
                        AND t.payment_status = 'SUCCESS'
                        AND (
                            (t.created_at >= PARSEDATETIME(CAST(:year AS VARCHAR) || '-' || CAST(:month AS VARCHAR) || '-01', 'yyyy-M-dd')
                             AND t.created_at < DATEADD('MONTH', 1, PARSEDATETIME(CAST(:year AS VARCHAR) || '-' || CAST(:month AS VARCHAR) || '-01', 'yyyy-M-dd')))
                            OR
                            (t.created_at >= PARSEDATETIME(CAST(:prevYear AS VARCHAR) || '-' || CAST(:prevMonth AS VARCHAR) || '-01', 'yyyy-M-dd')
                             AND t.created_at < DATEADD('MONTH', 1, PARSEDATETIME(CAST(:prevYear AS VARCHAR) || '-' || CAST(:prevMonth AS VARCHAR) || '-01', 'yyyy-M-dd')))
                        )
                    GROUP BY CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER), CAST(EXTRACT(MONTH FROM t.created_at) AS INTEGER)
                ),
                formatted_data AS (
                    SELECT
                        CAST("year" AS VARCHAR) AS "year",
                        FORMATDATETIME(PARSEDATETIME('2000-' || LPAD(CAST("month" AS VARCHAR), 2, '0') || '-01', 'yyyy-MM-dd'), 'MMM') AS "month",
                        CAST(total_success AS BIGINT) AS totalSuccess,
                        CAST(total_amount AS BIGINT) AS totalAmount,
                        "year" AS "sort_year",
                        "month" AS "sort_month"
                    FROM monthly_data
                    UNION ALL
                    SELECT CAST(:year AS VARCHAR),
                           FORMATDATETIME(PARSEDATETIME(CAST(:year AS VARCHAR) || '-' || CAST(:month AS VARCHAR) || '-01', 'yyyy-M-dd'), 'MMM'),
                           CAST(0 AS BIGINT), CAST(0 AS BIGINT), :year, :month
                    WHERE NOT EXISTS (
                        SELECT 1 FROM monthly_data
                        WHERE "year" = :year AND "month" = :month
                    )
                    UNION ALL
                    SELECT CAST(:prevYear AS VARCHAR),
                           FORMATDATETIME(PARSEDATETIME(CAST(:prevYear AS VARCHAR) || '-' || CAST(:prevMonth AS VARCHAR) || '-01', 'yyyy-M-dd'), 'MMM'),
                           CAST(0 AS BIGINT), CAST(0 AS BIGINT), :prevYear, :prevMonth
                    WHERE NOT EXISTS (
                        SELECT 1 FROM monthly_data
                        WHERE "year" = :prevYear AND "month" = :prevMonth
                    )
                )
                SELECT "year", "month", totalSuccess, totalAmount FROM formatted_data
                ORDER BY "sort_year" DESC, "sort_month" DESC
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("year", year);
            dataQuery.setParameter("month", month);
            dataQuery.setParameter("prevYear", prevYear);
            dataQuery.setParameter("prevMonth", prevMonth);

            return dataQuery.getResultList().map(results -> {
                List<TransactionMonthlyAmountSuccess> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    TransactionMonthlyAmountSuccess obj = new TransactionMonthlyAmountSuccess();
                    obj.setYear((String) row[0]);
                    obj.setMonth((String) row[1]);
                    obj.setTotalSuccess(((Number) row[2]).longValue());
                    obj.setTotalAmount(((Number) row[3]).longValue());
                    list.add(obj);
                }
                return list;
            });
        });
    }

    public Uni<List<TransactionYearlyAmountSuccess>> findYearlyTransactionSuccess(Integer year) {
        String sql = """
                WITH yearly_data AS (
                    SELECT
                        CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS "year",
                        COUNT(*) AS total_success,
                        CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                    FROM transactions t
                    WHERE
                        t.deleted_at IS NULL
                        AND t.payment_status = 'SUCCESS'
                        AND (EXTRACT(YEAR FROM t.created_at) = :year
                             OR EXTRACT(YEAR FROM t.created_at) = :year - 1)
                    GROUP BY CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER)
                ),
                formatted_data AS (
                    SELECT
                        CAST("year" AS VARCHAR) AS "year",
                        CAST(total_success AS BIGINT) AS totalSuccess,
                        CAST(total_amount AS BIGINT) AS totalAmount
                    FROM yearly_data
                    UNION ALL
                    SELECT CAST(:year AS VARCHAR), CAST(0 AS BIGINT), CAST(0 AS BIGINT) WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE "year" = :year)
                    UNION ALL
                    SELECT CAST((:year - 1) AS VARCHAR), CAST(0 AS BIGINT), CAST(0 AS BIGINT) WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE "year" = :year - 1)
                )
                SELECT * FROM formatted_data
                ORDER BY "year" DESC
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("year", year);

            return dataQuery.getResultList().map(results -> {
                List<TransactionYearlyAmountSuccess> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    TransactionYearlyAmountSuccess obj = new TransactionYearlyAmountSuccess();
                    obj.setYear((String) row[0]);
                    obj.setTotalSuccess(((Number) row[1]).longValue());
                    obj.setTotalAmount(((Number) row[2]).longValue());
                    list.add(obj);
                }
                return list;
            });
        });
    }

    public Uni<List<TransactionMonthlyAmountFailed>> findMonthlyTransactionFailed(
            Integer year,
            Integer month,
            Integer prevYear,
            Integer prevMonth) {

        String sql = """
                WITH monthly_data AS (
                    SELECT
                        CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS "year",
                        CAST(EXTRACT(MONTH FROM t.created_at) AS INTEGER) AS "month",
                        COUNT(*) AS total_failed,
                        CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                    FROM transactions t
                    WHERE
                        t.deleted_at IS NULL
                        AND t.payment_status = 'FAILED'
                        AND (
                            (t.created_at >= PARSEDATETIME(CAST(:year AS VARCHAR) || '-' || CAST(:month AS VARCHAR) || '-01', 'yyyy-M-dd')
                             AND t.created_at < DATEADD('MONTH', 1, PARSEDATETIME(CAST(:year AS VARCHAR) || '-' || CAST(:month AS VARCHAR) || '-01', 'yyyy-M-dd')))
                            OR
                            (t.created_at >= PARSEDATETIME(CAST(:prevYear AS VARCHAR) || '-' || CAST(:prevMonth AS VARCHAR) || '-01', 'yyyy-M-dd')
                             AND t.created_at < DATEADD('MONTH', 1, PARSEDATETIME(CAST(:prevYear AS VARCHAR) || '-' || CAST(:prevMonth AS VARCHAR) || '-01', 'yyyy-M-dd')))
                        )
                    GROUP BY CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER), CAST(EXTRACT(MONTH FROM t.created_at) AS INTEGER)
                ),
                formatted_data AS (
                    SELECT
                        CAST("year" AS VARCHAR) AS "year",
                        FORMATDATETIME(PARSEDATETIME('2000-' || LPAD(CAST("month" AS VARCHAR), 2, '0') || '-01', 'yyyy-MM-dd'), 'MMM') AS "month",
                        CAST(total_failed AS BIGINT) AS totalFailed,
                        CAST(total_amount AS BIGINT) AS totalAmount,
                        "year" AS "sort_year",
                        "month" AS "sort_month"
                    FROM monthly_data
                    UNION ALL
                    SELECT CAST(:year AS VARCHAR),
                           FORMATDATETIME(PARSEDATETIME(CAST(:year AS VARCHAR) || '-' || CAST(:month AS VARCHAR) || '-01', 'yyyy-M-dd'), 'MMM'),
                           CAST(0 AS BIGINT), CAST(0 AS BIGINT), :year, :month
                    WHERE NOT EXISTS (
                        SELECT 1 FROM monthly_data
                        WHERE "year" = :year AND "month" = :month
                    )
                    UNION ALL
                    SELECT CAST(:prevYear AS VARCHAR),
                           FORMATDATETIME(PARSEDATETIME(CAST(:prevYear AS VARCHAR) || '-' || CAST(:prevMonth AS VARCHAR) || '-01', 'yyyy-M-dd'), 'MMM'),
                           CAST(0 AS BIGINT), CAST(0 AS BIGINT), :prevYear, :prevMonth
                    WHERE NOT EXISTS (
                        SELECT 1 FROM monthly_data
                        WHERE "year" = :prevYear AND "month" = :prevMonth
                    )
                )
                SELECT "year", "month", totalFailed, totalAmount FROM formatted_data
                ORDER BY "sort_year" DESC, "sort_month" DESC
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("year", year);
            dataQuery.setParameter("month", month);
            dataQuery.setParameter("prevYear", prevYear);
            dataQuery.setParameter("prevMonth", prevMonth);

            return dataQuery.getResultList().map(results -> {
                List<TransactionMonthlyAmountFailed> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    TransactionMonthlyAmountFailed obj = new TransactionMonthlyAmountFailed();
                    obj.setYear((String) row[0]);
                    obj.setMonth((String) row[1]);
                    obj.setTotalFailed(((Number) row[2]).longValue());
                    obj.setTotalAmount(((Number) row[3]).longValue());
                    list.add(obj);
                }
                return list;
            });
        });
    }

    public Uni<List<TransactionYearlyAmountFailed>> findYearlyTransactionFailed(Integer year) {
        String sql = """
                WITH yearly_data AS (
                    SELECT
                        CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS "year",
                        COUNT(*) AS total_failed,
                        CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                    FROM transactions t
                    WHERE
                        t.deleted_at IS NULL
                        AND t.payment_status = 'FAILED'
                        AND (EXTRACT(YEAR FROM t.created_at) = :year
                             OR EXTRACT(YEAR FROM t.created_at) = :year - 1)
                    GROUP BY CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER)
                ),
                formatted_data AS (
                    SELECT
                        CAST("year" AS VARCHAR) AS "year",
                        CAST(total_failed AS BIGINT) AS totalFailed,
                        CAST(total_amount AS BIGINT) AS totalAmount
                    FROM yearly_data
                    UNION ALL
                    SELECT CAST(:year AS VARCHAR), CAST(0 AS BIGINT), CAST(0 AS BIGINT) WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE "year" = :year)
                    UNION ALL
                    SELECT CAST((:year - 1) AS VARCHAR), CAST(0 AS BIGINT), CAST(0 AS BIGINT) WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE "year" = :year - 1)
                )
                SELECT * FROM formatted_data
                ORDER BY "year" DESC
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("year", year);

            return dataQuery.getResultList().map(results -> {
                List<TransactionYearlyAmountFailed> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    TransactionYearlyAmountFailed obj = new TransactionYearlyAmountFailed();
                    obj.setYear((String) row[0]);
                    obj.setTotalFailed(((Number) row[1]).longValue());
                    obj.setTotalAmount(((Number) row[2]).longValue());
                    list.add(obj);
                }
                return list;
            });
        });
    }
}
