package com.example.service.merchantbusiness;

import com.example.domain.requests.merchantbusiness.CreateMerchantBusinessRequest;
import com.example.domain.requests.merchantbusiness.UpdateMerchantBusinessRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.merchantbusiness.MerchantBusinessResponse;
import com.example.domain.response.merchantbusiness.MerchantBusinessResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface MerchantBusinessCommandService {
    Uni<ApiResponse<MerchantBusinessResponse>> createMerchantBusiness(CreateMerchantBusinessRequest req);

    Uni<ApiResponse<MerchantBusinessResponse>> updateMerchantBusiness(UpdateMerchantBusinessRequest req);

    Uni<ApiResponse<MerchantBusinessResponseDeleteAt>> trashedMerchantBusiness(Long merchantBusinessInfoId);

    Uni<ApiResponse<MerchantBusinessResponseDeleteAt>> restoreMerchantBusiness(Long merchantBusinessInfoId);

    Uni<ApiResponse<Boolean>> deleteMerchantBusinessPermanent(Long merchantBusinessInfoId);

    Uni<ApiResponse<Boolean>> restoreAllMerchantBusiness();

    Uni<ApiResponse<Boolean>> deleteAllMerchantBusinessPermanent();
}
