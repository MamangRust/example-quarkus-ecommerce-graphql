package com.example.service.category.stats.totalprice;

import java.util.List;

import com.example.domain.requests.category.MonthTotalPriceRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.category.CategoriesMonthlyTotalPriceResponse;
import com.example.domain.response.category.CategoriesYearlyTotalPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryTotalPriceService {
    Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPrice(MonthTotalPriceRequest req);

    Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPrice(Integer year);
}
