package com.example.service.merchantdetail;

import java.util.List;

import com.example.domain.requests.merchant.FindAllMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.merchantdetail.MerchantDetailRelationResponse;
import com.example.domain.response.merchantdetail.MerchantDetailRelationResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface MerchantDetailQueryService {
    Uni<ApiResponsePagination<List<MerchantDetailRelationResponse>>> findAll(FindAllMerchantRequest req);

    Uni<ApiResponsePagination<List<MerchantDetailRelationResponseDeleteAt>>> findByActive(FindAllMerchantRequest req);

    Uni<ApiResponsePagination<List<MerchantDetailRelationResponseDeleteAt>>> findByTrashed(FindAllMerchantRequest req);

    Uni<ApiResponse<MerchantDetailRelationResponse>> findById(Long merchantID);
}
