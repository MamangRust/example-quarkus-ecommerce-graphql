package com.example.service;

import com.example.domain.requests.merchantsociallink.CreateMerchantSocialRequest;
import com.example.domain.requests.merchantsociallink.UpdateMerchantSocialRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.merchantsociallink.MerchantSocialMediaLinkResponse;
import com.example.domain.response.merchantsociallink.MerchantSocialMediaLinkResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface MerchantSocialLinkService {
    Uni<ApiResponse<MerchantSocialMediaLinkResponse>> create(CreateMerchantSocialRequest request);

    Uni<ApiResponse<MerchantSocialMediaLinkResponse>> update(UpdateMerchantSocialRequest request);

    Uni<ApiResponse<MerchantSocialMediaLinkResponseDeleteAt>> trash(Integer id);

    Uni<ApiResponse<MerchantSocialMediaLinkResponseDeleteAt>> restore(Integer id);

    Uni<ApiResponse<Boolean>> delete(Integer id);

    Uni<ApiResponse<Boolean>> restoreAll();

    Uni<ApiResponse<Boolean>> deleteAll();
}
