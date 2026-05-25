package com.example.service.category.stats.price;

import java.util.List;

import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.category.CategoriesMonthPriceResponse;
import com.example.domain.response.category.CategoriesYearPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryPriceService {
    Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPrice(Integer year);

    Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPrice(Integer year);
}
