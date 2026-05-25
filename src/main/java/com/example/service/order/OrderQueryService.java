package com.example.service.order;

import java.util.List;

import com.example.domain.requests.order.FindAllOrderByMerchantRequest;
import com.example.domain.requests.order.FindAllOrderRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.order.OrderRelationResponse;
import com.example.domain.response.order.OrderResponse;
import com.example.domain.response.order.OrderResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface OrderQueryService {
    Uni<ApiResponsePagination<List<OrderResponse>>> findAll(FindAllOrderRequest req);

    Uni<ApiResponsePagination<List<OrderResponseDeleteAt>>> findByActive(FindAllOrderRequest req);

    Uni<ApiResponsePagination<List<OrderResponseDeleteAt>>> findByTrashed(FindAllOrderRequest req);

    Uni<ApiResponsePagination<List<OrderResponse>>> findByMerchantId(FindAllOrderByMerchantRequest req);

    Uni<ApiResponse<OrderResponse>> findById(Long id);

    Uni<ApiResponse<OrderRelationResponse>> findOrderRelation(Long id);
}
