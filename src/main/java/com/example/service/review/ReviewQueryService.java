package com.example.service.review;

import java.util.List;

import com.example.domain.requests.review.FindAllReview;
import com.example.domain.requests.review.FindAllReviewByMerchant;
import com.example.domain.requests.review.FindAllReviewByProduct;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.reviews.ReviewRelationsDetailResponse;
import com.example.domain.response.reviews.ReviewResponse;
import com.example.domain.response.reviews.ReviewResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface ReviewQueryService {
    Uni<ApiResponsePagination<List<ReviewResponse>>> findAll(FindAllReview req);

    Uni<ApiResponsePagination<List<ReviewResponseDeleteAt>>> findActive(FindAllReview req);

    Uni<ApiResponsePagination<List<ReviewResponseDeleteAt>>> findTrashed(FindAllReview req);

    Uni<ApiResponsePagination<List<ReviewRelationsDetailResponse>>> findByMerchant(FindAllReviewByMerchant req);

    Uni<ApiResponsePagination<List<ReviewRelationsDetailResponse>>> findByProduct(FindAllReviewByProduct req);

    Uni<ApiResponse<ReviewResponse>> findById(Integer reviewId);
}
