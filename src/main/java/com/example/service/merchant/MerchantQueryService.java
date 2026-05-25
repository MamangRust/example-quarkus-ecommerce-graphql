package com.example.service.merchant;

import java.util.List;

import com.example.domain.requests.merchant.FindAllMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.merchant.MerchantResponse;
import com.example.domain.response.merchant.MerchantResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface MerchantQueryService {
    Uni<ApiResponsePagination<List<MerchantResponse>>> findAll(FindAllMerchantRequest req);

    Uni<ApiResponsePagination<List<MerchantResponseDeleteAt>>> findByActive(FindAllMerchantRequest req);

    Uni<ApiResponsePagination<List<MerchantResponseDeleteAt>>> findByTrashed(FindAllMerchantRequest req);

    Uni<ApiResponse<MerchantResponse>> findById(Long merchantId);

    Uni<ApiResponse<MerchantResponse>> findByUserId(Integer userId);
}
