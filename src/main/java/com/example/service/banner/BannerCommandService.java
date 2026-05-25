package com.example.service.banner;

import com.example.domain.requests.banner.CreateBannerRequest;
import com.example.domain.requests.banner.UpdateBannerRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.banner.BannerResponse;
import com.example.domain.response.banner.BannerResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface BannerCommandService {
    Uni<ApiResponse<BannerResponse>> createBanner(CreateBannerRequest request);

    Uni<ApiResponse<BannerResponse>> updateBanner(UpdateBannerRequest request);

    Uni<ApiResponse<BannerResponseDeleteAt>> trashedBanner(Long bannerId);

    Uni<ApiResponse<BannerResponseDeleteAt>> restoreBanner(Long bannerId);

    Uni<ApiResponse<Void>> deleteBannerPermanent(Long bannerId);

    Uni<ApiResponse<Void>> restoreAllBanner();

    Uni<ApiResponse<Void>> deleteAllBannerPermanent();
}
