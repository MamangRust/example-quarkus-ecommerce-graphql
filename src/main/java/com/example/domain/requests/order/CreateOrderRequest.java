package com.example.domain.requests.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

import com.example.domain.requests.shipping.CreateShippingAddressRequest;

@Data
public class CreateOrderRequest {
    @NotNull
    private Integer merchantId;

    @NotNull
    private Integer userId;

    @Valid
    @NotNull
    private List<CreateOrderItemRequest> items;

    @Valid
    @NotNull
    private CreateShippingAddressRequest shippingAddress;
}
