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
public class OrderItemResponseDeleteAt {
    private Long id;
    private Integer orderId;
    private Integer productId;
    private Integer quantity;
    private Integer price;
    private String createdAt;
    private String updatedAt;
    private String deletedAt;

    public static OrderItemResponseDeleteAt from(OrderItem entity) {
        return OrderItemResponseDeleteAt.builder()
                .id(entity.id)
                .orderId(entity.getOrderId())
                .productId(entity.getProductId())
                .quantity(entity.getQuantity())
                .price(entity.getPrice())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .deletedAt(entity.getDeletedAt() != null ? entity.getDeletedAt().toString() : null)
                .build();
    }
}