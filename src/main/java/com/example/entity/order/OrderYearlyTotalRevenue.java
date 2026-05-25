package com.example.entity.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderYearlyTotalRevenue {
    private String year;
    private Long totalRevenue;
}