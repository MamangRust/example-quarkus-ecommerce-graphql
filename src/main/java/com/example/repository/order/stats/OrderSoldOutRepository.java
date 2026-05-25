package com.example.repository.order.stats;

import java.util.ArrayList;
import java.util.List;

import com.example.entity.order.Order;
import com.example.entity.order.OrderMonthly;
import com.example.entity.order.OrderYearly;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderSoldOutRepository implements PanacheRepository<Order> {

    public Uni<List<OrderMonthly>> findMonthlyOrders(Integer yearMonth) {
        String sql = """
                WITH date_range AS (
                    SELECT
                        PARSEDATETIME(CAST(:yearMonth AS VARCHAR) || CASE WHEN LENGTH(CAST(:yearMonth AS VARCHAR)) = 4 THEN '0101' ELSE '01' END, 'yyyyMMdd') AS start_date,
                        DATEADD('DAY', -1, DATEADD('YEAR', 1, PARSEDATETIME(CAST(:yearMonth AS VARCHAR) || CASE WHEN LENGTH(CAST(:yearMonth AS VARCHAR)) = 4 THEN '0101' ELSE '01' END, 'yyyyMMdd'))) AS end_date
                ),
                monthly_orders AS (
                    SELECT
                        PARSEDATETIME(FORMATDATETIME(o.created_at, 'yyyy-MM-01'), 'yyyy-MM-01') AS activity_month,
                        CAST(COUNT(o.id) AS INTEGER) AS order_count,
                        CAST(SUM(o.total_price) AS BIGINT) AS total_revenue,
                        CAST(SUM(oi.quantity) AS INTEGER) AS total_items_sold
                    FROM orders o
                    JOIN order_items oi ON o.id = oi.order_id
                    WHERE o.deleted_at IS NULL
                      AND oi.deleted_at IS NULL
                      AND o.created_at BETWEEN (SELECT start_date FROM date_range)
                                           AND (SELECT end_date FROM date_range)
                    GROUP BY PARSEDATETIME(FORMATDATETIME(o.created_at, 'yyyy-MM-01'), 'yyyy-MM-01')
                )
                SELECT
                    FORMATDATETIME(mo.activity_month, 'MMM') AS "month",
                    mo.order_count AS orderCount,
                    mo.total_revenue AS totalRevenue,
                    mo.total_items_sold AS totalItemsSold
                FROM monthly_orders mo
                ORDER BY mo.activity_month
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("yearMonth", yearMonth);
            return dataQuery.getResultList().map(results -> {
                List<OrderMonthly> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    OrderMonthly om = new OrderMonthly();
                    om.setMonth((String) row[0]);
                    om.setOrderCount(((Number) row[1]).intValue());
                    om.setTotalRevenue(((Number) row[2]).longValue());
                    om.setTotalItemsSold(((Number) row[3]).intValue());
                    list.add(om);
                }
                return list;
            });
        });
    }

    public Uni<List<OrderYearly>> findYearlyOrders(Integer yearMonth) {
        String sql = """
                WITH last_five_years AS (
                    SELECT
                        CAST(EXTRACT(YEAR FROM o.created_at) AS VARCHAR) AS "year",
                        CAST(COUNT(o.id) AS INTEGER) AS order_count,
                        CAST(SUM(o.total_price) AS BIGINT) AS total_revenue,
                        CAST(SUM(oi.quantity) AS INTEGER) AS total_items_sold,
                        CAST(COUNT(DISTINCT o.user_id) AS INTEGER) AS active_cashiers,
                        CAST(COUNT(DISTINCT oi.product_id) AS INTEGER) AS unique_products_sold
                    FROM orders o
                    JOIN order_items oi ON o.id = oi.order_id
                    WHERE o.deleted_at IS NULL
                      AND oi.deleted_at IS NULL
                      AND EXTRACT(YEAR FROM o.created_at) BETWEEN EXTRACT(YEAR FROM PARSEDATETIME(CAST(:yearMonth AS VARCHAR) || CASE WHEN LENGTH(CAST(:yearMonth AS VARCHAR)) = 4 THEN '0101' ELSE '01' END, 'yyyyMMdd')) - 4
                                                               AND EXTRACT(YEAR FROM PARSEDATETIME(CAST(:yearMonth AS VARCHAR) || CASE WHEN LENGTH(CAST(:yearMonth AS VARCHAR)) = 4 THEN '0101' ELSE '01' END, 'yyyyMMdd'))
                    GROUP BY EXTRACT(YEAR FROM o.created_at)
                )
                SELECT
                    "year" AS "year",
                    order_count AS orderCount,
                    total_revenue AS totalRevenue,
                    total_items_sold AS totalItemsSold,
                    active_cashiers AS activeCashiers,
                    unique_products_sold AS uniqueProductsSold
                FROM last_five_years
                ORDER BY "year"
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("yearMonth", yearMonth);
            return dataQuery.getResultList().map(results -> {
                List<OrderYearly> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    OrderYearly oy = new OrderYearly();
                    oy.setYear((String) row[0]);
                    oy.setOrderCount(((Number) row[1]).intValue());
                    oy.setTotalRevenue(((Number) row[2]).longValue());
                    oy.setTotalItemsSold(((Number) row[3]).intValue());
                    oy.setActiveCashiers(((Number) row[4]).intValue());
                    oy.setUniqueProductsSold(((Number) row[5]).intValue());
                    list.add(oy);
                }
                return list;
            });
        });
    }
}