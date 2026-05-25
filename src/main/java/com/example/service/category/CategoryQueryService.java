package com.example.service.category;

import java.util.List;

import com.example.domain.requests.category.FindAllCategoryRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.category.CategoryResponse;
import com.example.domain.response.category.CategoryResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface CategoryQueryService {
    Uni<ApiResponsePagination<List<CategoryResponse>>> findAll(FindAllCategoryRequest req);

    Uni<ApiResponsePagination<List<CategoryResponseDeleteAt>>> findByActive(FindAllCategoryRequest req);

    Uni<ApiResponsePagination<List<CategoryResponseDeleteAt>>> findByTrashed(FindAllCategoryRequest req);

    Uni<ApiResponse<CategoryResponse>> findById(Long categoryId);
}
