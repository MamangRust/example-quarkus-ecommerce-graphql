package com.example.repository.category.statsbymerchant;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.example.entity.category.CategoriesMonthPrice;
import com.example.entity.category.CategoriesYearPrice;
import com.example.entity.category.Category;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CategoryPriceByMerchantRepository implements PanacheRepository<Category> {

    public Uni<List<CategoriesMonthPrice>> findMonthlyCategoryStatsByMerchant(Integer merchantId, Integer year) {
        String sql = """
                WITH
                    date_range AS (
                        SELECT
                            PARSEDATETIME(CAST(:year AS VARCHAR) || '-01-01', 'yyyy-MM-dd') AS start_date,
                            DATEADD('DAY', -1, DATEADD('YEAR', 1, PARSEDATETIME(CAST(:year AS VARCHAR) || '-01-01', 'yyyy-MM-dd'))) AS end_date
                    ),
                    monthly_category_stats AS (
                        SELECT
                            c.id AS category_id,
                            c.name AS category_name,
                            PARSEDATETIME(FORMATDATETIME(o.created_at, 'yyyy-MM-01'), 'yyyy-MM-dd') AS activity_month,
                            COUNT(DISTINCT o.id) AS order_count,
                            SUM(oi.quantity) AS items_sold,
                            CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalRevenue
                        FROM orders o
                        JOIN order_items oi ON o.id = oi.order_id
                        JOIN products p ON oi.product_id = p.id
                        JOIN categories c ON p.category_id = c.id
                        WHERE
                            o.deleted_at IS NULL
                            AND oi.deleted_at IS NULL
                            AND p.deleted_at IS NULL
                            AND c.deleted_at IS NULL
                            AND o.merchant_id = :merchantId
                            AND o.created_at BETWEEN (SELECT start_date FROM date_range) AND (SELECT end_date FROM date_range)
                        GROUP BY c.id, c.name, PARSEDATETIME(FORMATDATETIME(o.created_at, 'yyyy-MM-01'), 'yyyy-MM-dd')
                    )
                SELECT
                    FORMATDATETIME(mcs.activity_month, 'MMM') AS "month",
                    mcs.category_id AS categoryId,
                    mcs.category_name AS categoryName,
                    mcs.order_count AS orderCount,
                    mcs.items_sold AS itemsSold,
                    mcs.totalRevenue AS totalRevenue
                FROM monthly_category_stats mcs
                ORDER BY mcs.activity_month, mcs.totalRevenue DESC
                """;

        return Panache.getSession().chain(session -> session.createNativeQuery(sql)
                .setParameter("merchantId", merchantId)
                .setParameter("year", year)
                .getResultList()
                .map(list -> {
                    List<CategoriesMonthPrice> resultList = new ArrayList<>();
                    for (Object item : list) {
                        Object[] row = (Object[]) item;
                        resultList.add(new CategoriesMonthPrice(
                                (String) row[0],
                                row[1] != null ? ((Number) row[1]).longValue() : null,
                                (String) row[2],
                                row[3] != null ? ((Number) row[3]).longValue() : null,
                                row[4] != null ? ((Number) row[4]).longValue() : null,
                                row[5] != null ? ((Number) row[5]).longValue() : null));
                    }
                    return resultList;
                }));
    }

    public Uni<List<CategoriesYearPrice>> findYearlyCategoryStatsByMerchant(Integer merchantId, Integer year) {
        String sql = """
                WITH last_five_years AS (
                    SELECT
                        c.id AS category_id,
                        c.name AS category_name,
                        CAST(EXTRACT(YEAR FROM o.created_at) AS VARCHAR) AS "year",
                        COUNT(DISTINCT o.id) AS order_count,
                        SUM(oi.quantity) AS items_sold,
                        CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalRevenue,
                        COUNT(DISTINCT oi.product_id) AS unique_products_sold
                    FROM orders o
                    JOIN order_items oi ON o.id = oi.order_id
                    JOIN products p ON oi.product_id = p.id
                    JOIN categories c ON p.category_id = c.id
                    WHERE
                        o.deleted_at IS NULL
                        AND oi.deleted_at IS NULL
                        AND p.deleted_at IS NULL
                        AND c.deleted_at IS NULL
                        AND o.merchant_id = :merchantId
                        AND EXTRACT(YEAR FROM o.created_at) BETWEEN (:year - 4) AND :year
                    GROUP BY c.id, c.name, CAST(EXTRACT(YEAR FROM o.created_at) AS VARCHAR)
                )
                SELECT
                    "year" AS "year",
                    category_id AS categoryId,
                    category_name AS categoryName,
                    order_count AS orderCount,
                    items_sold AS itemsSold,
                    totalRevenue AS totalRevenue,
                    unique_products_sold AS uniqueProductsSold
                FROM last_five_years
                ORDER BY "year", totalRevenue DESC
                """;

        return Panache.getSession().chain(session -> session.createNativeQuery(sql)
                .setParameter("merchantId", merchantId)
                .setParameter("year", year)
                .getResultList()
                .map(list -> {
                    List<CategoriesYearPrice> resultList = new ArrayList<>();
                    for (Object item : list) {
                        Object[] row = (Object[]) item;
                        resultList.add(new CategoriesYearPrice(
                                (String) row[0],
                                row[1] != null ? ((Number) row[1]).longValue() : null,
                                (String) row[2],
                                row[3] != null ? ((Number) row[3]).longValue() : null,
                                row[4] != null ? ((Number) row[4]).longValue() : null,
                                row[5] != null ? ((Number) row[5]).longValue() : null,
                                row[6] != null ? ((Number) row[6]).longValue() : null));
                    }
                    return resultList;
                }));
    }
}