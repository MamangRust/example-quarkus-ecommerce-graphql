package com.example.domain.response.order;

import java.util.List;

import com.example.entity.order.OrderYearly;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderYearlyResponse {
    private String year;
    private Integer orderCount;
    private Long totalRevenue;
    private Integer totalItemsSold;
    private Integer activeCashiers;
    private Integer uniqueProductsSold;

    public static OrderYearlyResponse from(OrderYearly response) {
        return OrderYearlyResponse.builder()
                .year(response.getYear())
                .orderCount(response.getOrderCount())
                .totalRevenue((long) response.getTotalRevenue())
                .totalItemsSold(response.getTotalItemsSold())
                .activeCashiers(response.getActiveCashiers())
                .uniqueProductsSold(response.getUniqueProductsSold())
                .build();
    }

    public static List<OrderYearlyResponse> fromList(List<OrderYearly> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(OrderYearlyResponse::from).toList();
    }
}