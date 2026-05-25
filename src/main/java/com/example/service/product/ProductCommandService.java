package com.example.service.product;

import com.example.domain.requests.product.CreateProductRequest;
import com.example.domain.requests.product.UpdateProductRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.product.ProductResponse;
import com.example.domain.response.product.ProductResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface ProductCommandService {
    Uni<ApiResponse<ProductResponse>> createProduct(CreateProductRequest req);

    Uni<ApiResponse<ProductResponse>> updateProduct(UpdateProductRequest req);

    Uni<ApiResponse<ProductResponseDeleteAt>> trashedProduct(Integer productId);

    Uni<ApiResponse<ProductResponseDeleteAt>> restoreProduct(Integer productId);

    Uni<ApiResponse<Boolean>> deleteProductPermanent(Integer productId);

    Uni<ApiResponse<Boolean>> restoreAllProducts();

    Uni<ApiResponse<Boolean>> deleteAllProductsPermanent();
}
