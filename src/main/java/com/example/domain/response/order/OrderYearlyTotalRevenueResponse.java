package com.example.domain.response.order;

import java.util.List;

import com.example.entity.order.OrderYearlyTotalRevenue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderYearlyTotalRevenueResponse {
    private String year;
    private Long totalRevenue;

    public static OrderYearlyTotalRevenueResponse from(OrderYearlyTotalRevenue response) {
        return OrderYearlyTotalRevenueResponse.builder()
                .year(response.getYear())
                .totalRevenue(response.getTotalRevenue())
                .build();
    }

    public static List<OrderYearlyTotalRevenueResponse> fromList(List<OrderYearlyTotalRevenue> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(OrderYearlyTotalRevenueResponse::from).toList();
    }
}