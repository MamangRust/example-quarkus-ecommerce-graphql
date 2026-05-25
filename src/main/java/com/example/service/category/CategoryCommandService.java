package com.example.service.category;

import com.example.domain.requests.category.CreateCategoryRequest;
import com.example.domain.requests.category.UpdateCategoryRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.category.CategoryResponse;
import com.example.domain.response.category.CategoryResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface CategoryCommandService {
    Uni<ApiResponse<CategoryResponse>> createCategory(CreateCategoryRequest req);

    Uni<ApiResponse<CategoryResponse>> updateCategory(UpdateCategoryRequest req);

    Uni<ApiResponse<CategoryResponseDeleteAt>> trashedCategory(Long categoryId);

    Uni<ApiResponse<CategoryResponseDeleteAt>> restoreCategory(Long categoryId);

    Uni<ApiResponse<Void>> deleteCategoryPermanent(Long categoryId);

    Uni<ApiResponse<Void>> restoreAllCategories();

    Uni<ApiResponse<Void>> deleteAllCategoriesPermanent();
}
