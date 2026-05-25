package com.example.domain.requests.merchant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Data;

@Data
public class UpdateMerchantRequest {

    @Null
    private Integer merchantId;

    @NotNull
    private Integer userId;

    @NotBlank
    private String name;

    @NotBlank
    private String description;

    @NotBlank
    private String address;

    @NotBlank
    @Email
    private String contactEmail;

    @NotBlank
    private String contactPhone;

    @NotBlank
    private String status;
}
