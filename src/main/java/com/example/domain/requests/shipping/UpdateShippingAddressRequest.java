package com.example.domain.requests.shipping;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateShippingAddressRequest {

    @NotNull
    private Integer shippingId;

    private Integer orderId;

    private String alamat;

    private String provinsi;

    private String kota;

    @NotBlank
    private String courier;

    @NotBlank
    private String shippingMethod;

    @NotNull
    @Min(0)
    private Integer shippingCost;

    private String negara;
}
