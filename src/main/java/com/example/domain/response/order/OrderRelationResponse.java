package com.example.domain.response.order;

import java.util.List;

import com.example.domain.response.orderitem.OrderItemResponse;
import com.example.entity.order.OrderRelation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRelationResponse {
    private Long orderId;

    private Integer userId;

    private Integer merchantId;

    private Integer totalPrice;

    private List<OrderItemResponse> orderItems;

    public static OrderRelationResponse from(OrderRelation orderRelation) {
        return OrderRelationResponse.builder()
                .orderId(orderRelation.getOrderId())
                .userId(orderRelation.getUserId())
                .merchantId(orderRelation.getMerchantId())
                .totalPrice(orderRelation.getTotalPrice())
                .orderItems(orderRelation.getOrderItems().stream()
                        .map(OrderItemResponse::from)
                        .toList())
                .build();
    }
}
