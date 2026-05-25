package com.example.service.order.stats;

import java.util.List;

import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.order.OrderMonthlyResponse;
import com.example.domain.response.order.OrderYearlyResponse;

import io.smallrye.mutiny.Uni;

public interface OrderSoldoutService {
    Uni<ApiResponse<List<OrderMonthlyResponse>>> findMonthlyOrders(Integer yearMonth);

    Uni<ApiResponse<List<OrderYearlyResponse>>> findYearlyOrders(Integer yearMonth);
}
