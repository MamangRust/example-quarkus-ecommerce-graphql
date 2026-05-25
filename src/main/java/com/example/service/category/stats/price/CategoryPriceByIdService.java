package com.example.service.category.stats.price;

import java.util.List;

import com.example.domain.requests.category.MonthPriceIdRequest;
import com.example.domain.requests.category.YearPriceIdRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.category.CategoriesMonthPriceResponse;
import com.example.domain.response.category.CategoriesYearPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryPriceByIdService {
    Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceById(MonthPriceIdRequest req);

    Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceById(YearPriceIdRequest req);
}
