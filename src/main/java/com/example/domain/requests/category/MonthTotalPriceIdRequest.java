package com.example.domain.requests.category;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MonthTotalPriceIdRequest {

    private Integer categoryId;

    @NotNull
    private Integer year;

    @NotNull
    private Integer month;
}
