package com.example.repository.category.stats;

import java.util.ArrayList;
import java.util.List;

import com.example.entity.category.CategoriesMonthlyTotalPrice;
import com.example.entity.category.CategoriesYearlyTotalPrice;
import com.example.entity.category.Category;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CategoryTotalPriceRepository implements PanacheRepository<Category> {

    public Uni<List<CategoriesMonthlyTotalPrice>> findMonthlyTotalPrice(
            Integer startYear,
            Integer startMonth,
            Integer endYear,
            Integer endMonth) {
        String sql = """
                WITH monthly_totals AS (
                        SELECT
                            CAST(EXTRACT(YEAR FROM o.created_at) AS VARCHAR) AS "year",
                            CAST(EXTRACT(MONTH FROM o.created_at) AS INTEGER) AS month_num,
                            FORMATDATETIME(o.created_at, 'MMMM') AS month_name,
                            CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalRevenue
                        FROM orders o
                        JOIN order_items oi ON o.id = oi.order_id
                        JOIN products p ON oi.product_id = p.id
                        JOIN categories c ON p.category_id = c.id
                        WHERE
                            o.deleted_at IS NULL
                            AND oi.deleted_at IS NULL
                            AND (
                                (EXTRACT(YEAR FROM o.created_at) = :startYear AND EXTRACT(MONTH FROM o.created_at) = :startMonth)
                                OR
                                (EXTRACT(YEAR FROM o.created_at) = :endYear AND EXTRACT(MONTH FROM o.created_at) = :endMonth)
                            )
                        GROUP BY CAST(EXTRACT(YEAR FROM o.created_at) AS VARCHAR),
                                 CAST(EXTRACT(MONTH FROM o.created_at) AS INTEGER),
                                 FORMATDATETIME(o.created_at, 'MMMM')
                    ),
                    all_months AS (
                        SELECT CAST(:startYear AS VARCHAR) AS "year", CAST(:startMonth AS INTEGER) AS month_num,
                               FORMATDATETIME(PARSEDATETIME(CAST(:startYear AS VARCHAR) || '-' || CAST(:startMonth AS VARCHAR) || '-01', 'yyyy-M-dd'), 'MMMM') AS month_name
                        UNION
                        SELECT CAST(:endYear AS VARCHAR) AS "year", CAST(:endMonth AS INTEGER) AS month_num,
                               FORMATDATETIME(PARSEDATETIME(CAST(:endYear AS VARCHAR) || '-' || CAST(:endMonth AS VARCHAR) || '-01', 'yyyy-M-dd'), 'MMMM') AS month_name
                    )
                   SELECT
                        am."year" AS "year",
                        am.month_name AS "month",
                        COALESCE(mt.totalRevenue, 0) AS totalRevenue
                    FROM all_months am
                    LEFT JOIN monthly_totals mt
                        ON am."year" = mt."year" AND am.month_num = mt.month_num
                    ORDER BY CAST(am."year" AS INTEGER) DESC, am.month_num DESC
                """;

        return Panache.getSession().chain(session -> session.createNativeQuery(sql)
                .setParameter("startYear", startYear)
                .setParameter("startMonth", startMonth)
                .setParameter("endYear", endYear)
                .setParameter("endMonth", endMonth)
                .getResultList()
                .map(list -> {
                    List<CategoriesMonthlyTotalPrice> resultList = new ArrayList<>();
                    for (Object item : list) {
                        Object[] row = (Object[]) item;
                        resultList.add(new CategoriesMonthlyTotalPrice(
                                (String) row[0],
                                (String) row[1],
                                row[2] != null ? ((Number) row[2]).longValue() : null));
                    }
                    return resultList;
                }));
    }

    public Uni<List<CategoriesYearlyTotalPrice>> findYearlyTotalPrice(Integer year) {
        String sql = """
                WITH yearly_data AS (
                    SELECT
                        CAST(EXTRACT(YEAR FROM o.created_at) AS VARCHAR) AS "year",
                        CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalRevenue
                    FROM
                        orders o
                    JOIN
                        order_items oi ON o.id = oi.order_id
                    JOIN
                        products p ON oi.product_id = p.id
                    JOIN
                        categories c ON p.category_id = c.id
                    WHERE
                        o.deleted_at IS NULL
                        AND oi.deleted_at IS NULL
                        AND p.deleted_at IS NULL
                        AND c.deleted_at IS NULL
                        AND (
                            EXTRACT(YEAR FROM o.created_at) = :year
                            OR EXTRACT(YEAR FROM o.created_at) = :year - 1
                        )
                    GROUP BY
                        EXTRACT(YEAR FROM o.created_at)
                ),
                all_years AS (
                    SELECT :year AS "year"
                    UNION
                    SELECT :year - 1 AS "year"
                )
                SELECT
                    CAST(a."year" AS VARCHAR) AS "year",
                    CAST(COALESCE(yd.totalRevenue, 0) AS BIGINT) AS totalRevenue
                FROM
                    all_years a
                LEFT JOIN
                    yearly_data yd ON CAST(a."year" AS VARCHAR) = yd."year"
                ORDER BY
                    a."year" DESC
                """;

        return Panache.getSession().chain(session -> session.createNativeQuery(sql)
                .setParameter("year", year)
                .getResultList()
                .map(list -> {
                    List<CategoriesYearlyTotalPrice> resultList = new ArrayList<>();
                    for (Object item : list) {
                        Object[] row = (Object[]) item;
                        resultList.add(new CategoriesYearlyTotalPrice(
                                (String) row[0],
                                row[1] != null ? ((Number) row[1]).longValue() : null));
                    }
                    return resultList;
                }));
    }
}
