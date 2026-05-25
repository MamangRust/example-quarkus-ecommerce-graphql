package com.example.entity.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderMonthly {
    private String month;
    private Integer orderCount;
    private Long totalRevenue;
    private Integer totalItemsSold;
}