package com.example.domain.requests.category;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MonthTotalPriceRequest {

    @NotNull
    private Integer year;

    @NotNull
    private Integer month;
}
