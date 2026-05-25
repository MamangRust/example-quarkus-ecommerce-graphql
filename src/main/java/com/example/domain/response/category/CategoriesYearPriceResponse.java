package com.example.domain.response.category;

import java.util.List;

import com.example.entity.category.CategoriesYearPrice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriesYearPriceResponse {
    private String year;
    private Long categoryId;
    private String categoryName;
    private Long orderCount;
    private Long itemsSold;
    private Long totalRevenue;
    private Long uniqueProductsSold;

    public static CategoriesYearPriceResponse from(CategoriesYearPrice response) {
        return CategoriesYearPriceResponse.builder()
                .year(response.getYear())
                .categoryId(response.getCategoryId())
                .categoryName(response.getCategoryName())
                .orderCount(response.getOrderCount())
                .itemsSold(response.getItemsSold())
                .totalRevenue(response.getTotalRevenue())
                .uniqueProductsSold(response.getUniqueProductsSold())
                .build();
    }

    public static List<CategoriesYearPriceResponse> fromList(List<CategoriesYearPrice> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(CategoriesYearPriceResponse::from).toList();
    }
}