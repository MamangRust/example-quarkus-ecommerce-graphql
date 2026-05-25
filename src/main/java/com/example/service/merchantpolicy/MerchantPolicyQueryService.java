package com.example.service.merchantpolicy;

import java.util.List;

import com.example.domain.requests.merchant.FindAllMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.merchantpolicy.MerchantPoliciesResponse;
import com.example.domain.response.merchantpolicy.MerchantPoliciesResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface MerchantPolicyQueryService {
    Uni<ApiResponsePagination<List<MerchantPoliciesResponse>>> findAll(FindAllMerchantRequest req);

    Uni<ApiResponsePagination<List<MerchantPoliciesResponseDeleteAt>>> findByActive(FindAllMerchantRequest req);

    Uni<ApiResponsePagination<List<MerchantPoliciesResponseDeleteAt>>> findByTrashed(FindAllMerchantRequest req);

    Uni<ApiResponse<MerchantPoliciesResponse>> findById(Long id);
}
