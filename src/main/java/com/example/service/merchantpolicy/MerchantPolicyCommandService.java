package com.example.service.merchantpolicy;

import com.example.domain.requests.merchantpolicy.CreateMerchantPolicyRequest;
import com.example.domain.requests.merchantpolicy.UpdateMerchantPolicyRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.merchantpolicy.MerchantPoliciesResponse;
import com.example.domain.response.merchantpolicy.MerchantPoliciesResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface MerchantPolicyCommandService {
    Uni<ApiResponse<MerchantPoliciesResponse>> create(CreateMerchantPolicyRequest request);

    Uni<ApiResponse<MerchantPoliciesResponse>> update(UpdateMerchantPolicyRequest request);

    Uni<ApiResponse<MerchantPoliciesResponseDeleteAt>> trash(Long id);

    Uni<ApiResponse<MerchantPoliciesResponseDeleteAt>> restore(Long id);

    Uni<ApiResponse<Boolean>> delete(Long id);

    Uni<ApiResponse<Boolean>> restoreAll();

    Uni<ApiResponse<Boolean>> deleteAll();
}
