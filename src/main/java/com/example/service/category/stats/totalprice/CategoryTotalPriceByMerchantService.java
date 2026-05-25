package com.example.service.category.stats.totalprice;

import java.util.List;

import com.example.domain.requests.category.MonthTotalPriceMerchantRequest;
import com.example.domain.requests.category.YearTotalPriceMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.category.CategoriesMonthlyTotalPriceResponse;
import com.example.domain.response.category.CategoriesYearlyTotalPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryTotalPriceByMerchantService {
    Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPriceByMerchant(
            MonthTotalPriceMerchantRequest req);

    Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPriceByMerchant(
            YearTotalPriceMerchantRequest req);
}
