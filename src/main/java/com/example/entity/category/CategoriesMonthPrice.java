package com.example.entity.category;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoriesMonthPrice {
    private String month;
    private Long categoryId;
    private String categoryName;
    private Long orderCount;
    private Long itemsSold;
    private Long totalRevenue;
}