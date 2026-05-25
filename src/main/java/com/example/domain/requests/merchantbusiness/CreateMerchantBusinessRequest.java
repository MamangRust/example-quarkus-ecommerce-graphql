package com.example.domain.requests.merchantbusiness;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateMerchantBusinessRequest {

    @NotNull
    private Integer merchantId;

    @NotBlank
    private String businessType;

    @NotBlank
    private String taxId;

    @NotNull
    @Min(1900)
    @Max(2100)
    private Integer establishedYear;

    @NotNull
    @Min(1)
    private Integer numberOfEmployees;

    private String websiteUrl;
}
