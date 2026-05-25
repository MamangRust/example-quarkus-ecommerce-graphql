package com.example.domain.requests.category;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class YearTotalPriceMerchantRequest {

    @NotNull
    private Integer merchantId;

    @NotNull
    private Integer year;
}
