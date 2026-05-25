package com.example.service.order.statsbymerchant;

import java.util.List;

import com.example.domain.requests.order.MonthOrderMerchantRequest;
import com.example.domain.requests.order.YearOrderMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.order.OrderMonthlyResponse;
import com.example.domain.response.order.OrderYearlyResponse;

import io.smallrye.mutiny.Uni;

public interface OrderSoldOutByMerchantService {
    Uni<ApiResponse<List<OrderMonthlyResponse>>> findMonthlyOrdersByMerchant(MonthOrderMerchantRequest req);

    Uni<ApiResponse<List<OrderYearlyResponse>>> findYearlyOrdersByMerchant(YearOrderMerchantRequest req);
}
