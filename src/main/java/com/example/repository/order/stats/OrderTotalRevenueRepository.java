package com.example.repository.order.stats;

import java.util.ArrayList;
import java.util.List;

import com.example.entity.order.Order;
import com.example.entity.order.OrderMonthlyTotalRevenue;
import com.example.entity.order.OrderYearlyTotalRevenue;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderTotalRevenueRepository implements PanacheRepository<Order> {

    public Uni<List<OrderMonthlyTotalRevenue>> findMonthlyTotalRevenue(
            Integer year1,
            Integer month1,
            Integer year2,
            Integer month2) {

        String sql = """
                WITH monthly_revenue AS (
                    SELECT
                        CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER) AS "year",
                        CAST(EXTRACT(MONTH FROM o.created_at) AS INTEGER) AS month_num,
                        CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS total_revenue
                    FROM orders o
                    JOIN order_items oi ON o.id = oi.order_id
                    WHERE o.deleted_at IS NULL
                      AND oi.deleted_at IS NULL
                      AND (
                          (EXTRACT(YEAR FROM o.created_at) = :year1 AND EXTRACT(MONTH FROM o.created_at) = :month1)
                          OR (EXTRACT(YEAR FROM o.created_at) = :year2 AND EXTRACT(MONTH FROM o.created_at) = :month2)
                      )
                    GROUP BY CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER), CAST(EXTRACT(MONTH FROM o.created_at) AS INTEGER)
                ),
                all_months AS (
                    SELECT CAST(:year1 AS VARCHAR) AS "year", CAST(:month1 AS INTEGER) AS month_num, FORMATDATETIME(PARSEDATETIME('2000-' || LPAD(CAST(:month1 AS VARCHAR), 2, '0') || '-01', 'yyyy-MM-dd'), 'MMMM') AS month_name
                    UNION
                    SELECT CAST(:year2 AS VARCHAR) AS "year", CAST(:month2 AS INTEGER) AS month_num, FORMATDATETIME(PARSEDATETIME('2000-' || LPAD(CAST(:month2 AS VARCHAR), 2, '0') || '-01', 'yyyy-MM-dd'), 'MMMM') AS month_name
                )
                SELECT
                    am."year" AS "year",
                    am.month_name AS "month",
                    CAST(COALESCE(mr.total_revenue, 0) AS BIGINT) AS totalRevenue
                FROM all_months am
                LEFT JOIN monthly_revenue mr
                ON CAST(am."year" AS INTEGER) = mr."year" AND am.month_num = mr.month_num
                ORDER BY am."year" DESC, am.month_num DESC
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("year1", year1);
            dataQuery.setParameter("month1", month1);
            dataQuery.setParameter("year2", year2);
            dataQuery.setParameter("month2", month2);

            return dataQuery.getResultList().map(results -> {
                List<OrderMonthlyTotalRevenue> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    OrderMonthlyTotalRevenue om = new OrderMonthlyTotalRevenue();
                    om.setYear((String) row[0]);
                    om.setMonth((String) row[1]);
                    om.setTotalRevenue(((Number) row[2]).longValue());
                    list.add(om);
                }
                return list;
            });
        });
    }

    public Uni<List<OrderYearlyTotalRevenue>> findYearlyTotalRevenue(Integer year) {
        String sql = """
                WITH yearly_revenue AS (
                    SELECT
                        CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER) AS "year",
                        CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS total_revenue
                    FROM orders o
                    JOIN order_items oi ON o.id = oi.order_id
                    WHERE o.deleted_at IS NULL
                      AND oi.deleted_at IS NULL
                      AND (EXTRACT(YEAR FROM o.created_at) = :year OR EXTRACT(YEAR FROM o.created_at) = :year - 1)
                    GROUP BY CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER)
                ),
                all_years AS (
                    SELECT :year AS "year"
                    UNION
                    SELECT :year - 1 AS "year"
                )
                SELECT
                    CAST(ay."year" AS VARCHAR) AS "year",
                    CAST(COALESCE(yr.total_revenue, 0) AS BIGINT) AS totalRevenue
                FROM all_years ay
                LEFT JOIN yearly_revenue yr ON ay."year" = yr."year"
                ORDER BY ay."year" DESC
                """;

        return Panache.getSession().chain(session -> {
            var dataQuery = session.createNativeQuery(sql);
            dataQuery.setParameter("year", year);

            return dataQuery.getResultList().map(results -> {
                List<OrderYearlyTotalRevenue> list = new ArrayList<>();
                for (Object item : results) {
                    Object[] row = (Object[]) item;
                    OrderYearlyTotalRevenue oy = new OrderYearlyTotalRevenue();
                    oy.setYear((String) row[0]);
                    oy.setTotalRevenue(((Number) row[1]).longValue());
                    list.add(oy);
                }
                return list;
            });
        });
    }
}
