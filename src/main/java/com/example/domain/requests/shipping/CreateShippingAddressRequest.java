package com.example.domain.requests.shipping;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateShippingAddressRequest {

    private Integer orderId;

    @NotBlank
    private String alamat;

    @NotBlank
    private String provinsi;

    @NotBlank
    private String kota;

    @NotBlank
    private String courier;

    @NotBlank
    private String shippingMethod;

    @NotNull
    @Min(0)
    private Integer shippingCost;

    @NotBlank
    private String negara;
}
