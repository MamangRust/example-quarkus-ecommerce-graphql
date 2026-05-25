package com.example.service.merchantbusiness;

import java.util.List;

import com.example.domain.requests.merchant.FindAllMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.merchantbusiness.MerchantBusinessResponse;
import com.example.domain.response.merchantbusiness.MerchantBusinessResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface MerchantBusinessQueryService {
    Uni<ApiResponsePagination<List<MerchantBusinessResponse>>> findAll(FindAllMerchantRequest req);

    Uni<ApiResponsePagination<List<MerchantBusinessResponseDeleteAt>>> findByActive(FindAllMerchantRequest req);

    Uni<ApiResponsePagination<List<MerchantBusinessResponseDeleteAt>>> findByTrashed(FindAllMerchantRequest req);

    Uni<ApiResponse<MerchantBusinessResponse>> findById(Long merchantBusinessInfoId);
}
