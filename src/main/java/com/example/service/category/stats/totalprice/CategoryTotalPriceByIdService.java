package com.example.service.category.stats.totalprice;

import java.util.List;

import com.example.domain.requests.category.MonthTotalPriceIdRequest;
import com.example.domain.requests.category.YearTotalPriceIdRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.category.CategoriesMonthlyTotalPriceResponse;
import com.example.domain.response.category.CategoriesYearlyTotalPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryTotalPriceByIdService {
    Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPriceById(MonthTotalPriceIdRequest req);

    Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPriceById(YearTotalPriceIdRequest req);
}
