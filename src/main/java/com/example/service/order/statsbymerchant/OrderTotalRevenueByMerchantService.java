package com.example.service.order.statsbymerchant;

import java.util.List;

import com.example.domain.requests.order.MonthTotalRevenueMerchantRequest;
import com.example.domain.requests.order.YearTotalRevenueMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.order.OrderMonthlyTotalRevenueResponse;
import com.example.domain.response.order.OrderYearlyTotalRevenueResponse;

import io.smallrye.mutiny.Uni;

public interface OrderTotalRevenueByMerchantService {
    Uni<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> findMonthlyStatsByMerchant(
            MonthTotalRevenueMerchantRequest req);

    Uni<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> findYearlyStatsByMerchant(
            YearTotalRevenueMerchantRequest req);
}
