package com.example.domain.requests.order;

import java.util.List;

import com.example.domain.requests.shipping.UpdateShippingAddressRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateOrderRequest {
    @NotNull
    private Integer orderId;

    @NotNull
    private Integer userId;

    @Valid
    @NotNull
    private List<UpdateOrderItemRequest> items;

    @Valid
    @NotNull
    private UpdateShippingAddressRequest shippingAddress;
}
