package com.example.domain.requests.merchantpolicy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class UpdateMerchantPolicyRequest {

    @NotNull
    private Integer merchantPolicyId;

    @NotBlank
    private String policyType;

    @NotBlank
    private String title;

    @NotBlank
    private String description;
}
