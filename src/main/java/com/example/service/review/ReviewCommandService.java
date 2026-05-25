package com.example.service.review;

import com.example.domain.requests.review.CreateReviewRequest;
import com.example.domain.requests.review.UpdateReviewRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.reviews.ReviewResponse;
import com.example.domain.response.reviews.ReviewResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface ReviewCommandService {
    Uni<ApiResponse<ReviewResponse>> create(CreateReviewRequest request);

    Uni<ApiResponse<ReviewResponse>> update(UpdateReviewRequest request);

    Uni<ApiResponse<ReviewResponseDeleteAt>> trash(Integer id);

    Uni<ApiResponse<ReviewResponseDeleteAt>> restore(Integer id);

    Uni<ApiResponse<Boolean>> delete(Integer id);

    Uni<ApiResponse<Boolean>> restoreAll();

    Uni<ApiResponse<Boolean>> deleteAll();
}
