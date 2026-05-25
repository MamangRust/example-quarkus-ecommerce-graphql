package com.example.service.category.stats.price;

import java.util.List;

import com.example.domain.requests.category.MonthPriceMerchantRequest;
import com.example.domain.requests.category.YearPriceMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.category.CategoriesMonthPriceResponse;
import com.example.domain.response.category.CategoriesYearPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryPriceByMerchantService {
    Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceByMerchant(MonthPriceMerchantRequest req);

    Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceByMerchant(YearPriceMerchantRequest req);
}
