package com.example.service.merchantaward;

import com.example.domain.requests.merchantawrd.CreateMerchantAwardRequest;
import com.example.domain.requests.merchantawrd.UpdateMerchantAwardRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.merchantaward.MerchantAwardResponse;
import com.example.domain.response.merchantaward.MerchantAwardResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface MerchantAwardCommandService {
    Uni<ApiResponse<MerchantAwardResponse>> createMerchantAward(CreateMerchantAwardRequest req);

    Uni<ApiResponse<MerchantAwardResponse>> updateMerchantAward(UpdateMerchantAwardRequest req);

    Uni<ApiResponse<MerchantAwardResponseDeleteAt>> trashedMerchantAward(Long merchantAwardId);

    Uni<ApiResponse<MerchantAwardResponseDeleteAt>> restoreMerchantAward(Long merchantAwardId);

    Uni<ApiResponse<Boolean>> deleteMerchantAwardPermanent(Long merchantAwardId);

    Uni<ApiResponse<Boolean>> restoreAllMerchantAward();

    Uni<ApiResponse<Boolean>> deleteAllMerchantAwardPermanent();
}
