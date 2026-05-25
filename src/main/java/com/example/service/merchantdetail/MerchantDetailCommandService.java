package com.example.service.merchantdetail;

import com.example.domain.requests.merchantdetail.CreateMerchantDetailRequest;
import com.example.domain.requests.merchantdetail.UpdateMerchantDetailRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.merchantdetail.MerchantDetailResponse;
import com.example.domain.response.merchantdetail.MerchantDetailResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface MerchantDetailCommandService {
    Uni<ApiResponse<MerchantDetailResponse>> createMerchant(CreateMerchantDetailRequest req);

    Uni<ApiResponse<MerchantDetailResponse>> updateMerchant(UpdateMerchantDetailRequest req);

    Uni<ApiResponse<MerchantDetailResponseDeleteAt>> trashedMerchant(Long merchantID);

    Uni<ApiResponse<MerchantDetailResponseDeleteAt>> restoreMerchant(Long merchantID);

    Uni<ApiResponse<Boolean>> deleteMerchantPermanent(Long merchantID);

    Uni<ApiResponse<Boolean>> restoreAllMerchant();

    Uni<ApiResponse<Boolean>> deleteAllMerchantPermanent();
}
