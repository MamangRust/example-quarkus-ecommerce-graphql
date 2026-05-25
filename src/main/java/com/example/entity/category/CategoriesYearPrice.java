package com.example.entity.category;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoriesYearPrice {
    private String year;
    private Long categoryId;
    private String categoryName;
    private Long orderCount;
    private Long itemsSold;
    private Long totalRevenue;
    private Long uniqueProductsSold;
}
