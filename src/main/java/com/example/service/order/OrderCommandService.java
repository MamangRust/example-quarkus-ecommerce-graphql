package com.example.service.order;

import com.example.domain.requests.order.CreateOrderRequest;
import com.example.domain.requests.order.UpdateOrderRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.order.OrderResponse;
import com.example.domain.response.order.OrderResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface OrderCommandService {
    Uni<ApiResponse<OrderResponse>> create(CreateOrderRequest request);

    Uni<ApiResponse<OrderResponse>> update(UpdateOrderRequest request);

    Uni<ApiResponse<OrderResponseDeleteAt>> trash(Long id);

    Uni<ApiResponse<OrderResponseDeleteAt>> restore(Long id);

    Uni<ApiResponse<Boolean>> delete(Long id);

    Uni<ApiResponse<Boolean>> restoreAll();

    Uni<ApiResponse<Boolean>> deleteAll();
}
