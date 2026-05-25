package com.example.entity.order;

import java.util.List;

import com.example.entity.OrderItem;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderRelation {
    private Long orderId;

    private Integer userId;

    private Integer merchantId;

    private Integer totalPrice;

    private List<OrderItem> orderItems;
}
