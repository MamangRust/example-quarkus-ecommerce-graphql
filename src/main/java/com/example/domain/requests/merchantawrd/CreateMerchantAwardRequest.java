package com.example.domain.requests.merchantawrd;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateMerchantAwardRequest {

    @NotNull
    private Integer merchantId;

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotBlank
    private String issuedBy;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private String issueDate;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private String expiryDate;

    private String certificateUrl;
}
