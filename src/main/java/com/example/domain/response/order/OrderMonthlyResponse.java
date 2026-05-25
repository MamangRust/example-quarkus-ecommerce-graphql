package com.example.domain.response.order;

import java.util.List;

import com.example.entity.order.OrderMonthly;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderMonthlyResponse {
    private String month;
    private Integer orderCount;
    private Long totalRevenue;
    private Integer totalItemsSold;

    public static OrderMonthlyResponse from(OrderMonthly response) {
        return OrderMonthlyResponse.builder()
                .month(response.getMonth())
                .orderCount(response.getOrderCount())
                .totalRevenue((long) response.getTotalRevenue())
                .totalItemsSold(response.getTotalItemsSold())
                .build();
    }

    public static List<OrderMonthlyResponse> fromList(List<OrderMonthly> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(OrderMonthlyResponse::from).toList();
    }
}