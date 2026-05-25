package com.example.service.shippingaddress;

import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.shipping.ShippingAddressResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface ShippingAddressCommand {
    Uni<ApiResponse<ShippingAddressResponseDeleteAt>> trash(Integer shippingId);

    Uni<ApiResponse<ShippingAddressResponseDeleteAt>> restore(Integer shippingId);

    Uni<ApiResponse<Boolean>> deletePermanently(Integer shippingId);

    Uni<ApiResponse<Boolean>> restoreAll();

    Uni<ApiResponse<Boolean>> deleteAllPermanent();
}
