package com.example.service.banner;

import java.util.List;

import com.example.domain.requests.banner.FindAllBannerRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.banner.BannerResponse;
import com.example.domain.response.banner.BannerResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface BannerQueryService {
    Uni<ApiResponsePagination<List<BannerResponse>>> findAll(FindAllBannerRequest request);

    Uni<ApiResponsePagination<List<BannerResponseDeleteAt>>> findByActive(FindAllBannerRequest request);

    Uni<ApiResponsePagination<List<BannerResponseDeleteAt>>> findByTrashed(FindAllBannerRequest request);

    Uni<ApiResponse<BannerResponse>> findById(Long id);
}
