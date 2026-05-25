package com.example.entity.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderYearly {
    private String year;
    private Integer orderCount;
    private Long totalRevenue;
    private Integer totalItemsSold;
    private Integer activeCashiers;
    private Integer uniqueProductsSold;
}