package com.example.service.merchant;

import com.example.domain.requests.merchant.CreateMerchantRequest;
import com.example.domain.requests.merchant.UpdateMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.merchant.MerchantResponse;
import com.example.domain.response.merchant.MerchantResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface MerchantCommandService {
    Uni<ApiResponse<MerchantResponse>> createMerchant(CreateMerchantRequest req);

    Uni<ApiResponse<MerchantResponse>> updateMerchant(UpdateMerchantRequest req);

    Uni<ApiResponse<MerchantResponseDeleteAt>> trashedMerchant(Long merchantId);

    Uni<ApiResponse<MerchantResponseDeleteAt>> restoreMerchant(Long merchantId);

    Uni<ApiResponse<Boolean>> deleteMerchantPermanent(Long merchantId);

    Uni<ApiResponse<Boolean>> restoreAllMerchant();

    Uni<ApiResponse<Boolean>> deleteAllMerchantPermanent();
}
