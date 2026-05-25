package com.example.service.merchantaward;

import java.util.List;

import com.example.domain.requests.merchant.FindAllMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.merchantaward.MerchantAwardResponse;
import com.example.domain.response.merchantaward.MerchantAwardResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface MerchantAwardQueryService {
    Uni<ApiResponsePagination<List<MerchantAwardResponse>>> findAll(FindAllMerchantRequest req);

    Uni<ApiResponsePagination<List<MerchantAwardResponseDeleteAt>>> findByActive(FindAllMerchantRequest req);

    Uni<ApiResponsePagination<List<MerchantAwardResponseDeleteAt>>> findByTrashed(FindAllMerchantRequest req);

    Uni<ApiResponse<MerchantAwardResponse>> findById(Long merchantAwardId);
}
