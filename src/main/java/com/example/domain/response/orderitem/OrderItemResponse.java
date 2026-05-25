package com.example.domain.response.orderitem;

import com.example.entity.OrderItem;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    private Long id;
    private Integer orderId;
    private Integer productId;
    private Integer quantity;
    private Integer price;
    private String createdAt;
    private String updatedAt;

    public static OrderItemResponse from(OrderItem entity) {
        return OrderItemResponse.builder()
                .id(entity.id)
                .orderId(entity.getOrderId())
                .productId(entity.getProductId())
                .quantity(entity.getQuantity())
                .price(entity.getPrice())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }
}