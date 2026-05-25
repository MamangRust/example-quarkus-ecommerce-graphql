package com.example.service.reviewdetail;

import java.util.List;

import com.example.domain.requests.reviewdetail.CreateReviewDetailRequest;
import com.example.domain.requests.reviewdetail.UpdateReviewDetailRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.reviewdetail.ReviewDetailResponse;
import com.example.domain.response.reviewdetail.ReviewDetailResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface ReviewDetailService {
    Uni<ApiResponse<List<ReviewDetailResponse>>> create(List<CreateReviewDetailRequest> requests);

    Uni<ApiResponse<List<ReviewDetailResponse>>> update(List<UpdateReviewDetailRequest> requests);

    Uni<ApiResponse<ReviewDetailResponseDeleteAt>> trash(Integer reviewDetailId);

    Uni<ApiResponse<ReviewDetailResponseDeleteAt>> restore(Integer reviewDetailId);

    Uni<ApiResponse<Boolean>> delete(Integer reviewDetailId);

    Uni<ApiResponse<Boolean>> restoreAll();

    Uni<ApiResponse<Boolean>> deleteAll();
}
