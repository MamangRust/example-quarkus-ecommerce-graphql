package com.example.domain.requests.category;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class YearPriceIdRequest {

    private Integer categoryId;

    @NotNull
    private Integer year;
}
