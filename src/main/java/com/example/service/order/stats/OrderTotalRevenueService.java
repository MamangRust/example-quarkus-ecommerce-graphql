package com.example.service.order.stats;

import java.util.List;

import com.example.domain.requests.order.MonthTotalRevenue;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.order.OrderMonthlyTotalRevenueResponse;
import com.example.domain.response.order.OrderYearlyTotalRevenueResponse;

import io.smallrye.mutiny.Uni;

public interface OrderTotalRevenueService {
    Uni<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> findMonthlyStats(MonthTotalRevenue req);

    Uni<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> findYearlyStats(Integer year);
}
