package com.example.domain.requests.category;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class YearPriceMerchantRequest {

    @NotNull
    private Integer merchantId;

    @NotNull
    private Integer year;
}
