package com.example.entity.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderMonthlyTotalRevenue {
    private String year;
    private String month;
    private Long totalRevenue;
}