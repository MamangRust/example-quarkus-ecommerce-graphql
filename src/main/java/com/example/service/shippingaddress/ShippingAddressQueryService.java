package com.example.service.shippingaddress;

import java.util.List;

import com.example.domain.requests.shipping.FindAllShippingAddress;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.shipping.ShippingAddressResponse;
import com.example.domain.response.shipping.ShippingAddressResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface ShippingAddressQueryService {
    Uni<ApiResponsePagination<List<ShippingAddressResponse>>> findAll(FindAllShippingAddress req);

    Uni<ApiResponsePagination<List<ShippingAddressResponseDeleteAt>>> findByActive(FindAllShippingAddress req);

    Uni<ApiResponsePagination<List<ShippingAddressResponseDeleteAt>>> findByTrashed(FindAllShippingAddress req);

    Uni<ApiResponse<ShippingAddressResponse>> findById(Integer shippingId);

    Uni<ApiResponse<ShippingAddressResponse>> findByOrder(Integer orderId);
}
