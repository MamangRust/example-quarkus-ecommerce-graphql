package com.example.service.product;

import java.util.List;

import com.example.domain.requests.product.FindAllProductByCategoryRequest;
import com.example.domain.requests.product.FindAllProductByMerchantRequest;
import com.example.domain.requests.product.FindAllProductRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.product.ProductResponse;
import com.example.domain.response.product.ProductResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface ProductQueryService {
    Uni<ApiResponsePagination<List<ProductResponse>>> findAll(FindAllProductRequest req);

    Uni<ApiResponsePagination<List<ProductResponseDeleteAt>>> findActiveProducts(FindAllProductRequest req);

    Uni<ApiResponsePagination<List<ProductResponseDeleteAt>>> findTrashedProducts(FindAllProductRequest req);

    Uni<ApiResponsePagination<List<ProductResponse>>> findByMerchant(FindAllProductByMerchantRequest req);

    Uni<ApiResponsePagination<List<ProductResponse>>> findByCategoryName(FindAllProductByCategoryRequest req);

    Uni<ApiResponse<ProductResponse>> findById(Long productId);
}
