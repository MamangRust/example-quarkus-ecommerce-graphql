package com.example.service;

import java.util.List;

import com.example.domain.requests.cart.CreateCartRequest;
import com.example.domain.requests.cart.DeleteCartRequest;
import com.example.domain.requests.cart.FindAllCartsRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.cart.CartResponse;

import io.smallrye.mutiny.Uni;

public interface CartService {
    Uni<ApiResponsePagination<List<CartResponse>>> findAll(FindAllCartsRequest request);

    Uni<ApiResponse<CartResponse>> createCart(CreateCartRequest request);

    Uni<ApiResponse<Void>> deletePermanent(Long cartId);

    Uni<ApiResponse<Void>> deleteAllPermanently(DeleteCartRequest request);
}
