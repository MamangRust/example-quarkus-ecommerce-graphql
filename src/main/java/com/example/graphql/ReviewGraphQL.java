package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.review.*;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.reviews.ReviewRelationsDetailResponse;
import com.example.domain.response.reviews.ReviewResponse;
import com.example.domain.response.reviews.ReviewResponseDeleteAt;
import com.example.service.review.ReviewCommandService;
import com.example.service.review.ReviewQueryService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class ReviewGraphQL {

    @Inject
    ReviewQueryService reviewQueryService;

    @Inject
    ReviewCommandService reviewCommandService;

    @Query
    @Description("Find all reviews")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<ReviewResponse>>> findAllReviews(@Name("request") FindAllReview req) {
        return reviewQueryService.findAll(req);
    }

    @Query
    @Description("Find active reviews")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponsePagination<List<ReviewResponseDeleteAt>>> findActiveReviews(@Name("request") FindAllReview req) {
        return reviewQueryService.findActive(req);
    }

    @Query
    @Description("Find trashed reviews")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponsePagination<List<ReviewResponseDeleteAt>>> findTrashedReviews(@Name("request") FindAllReview req) {
        return reviewQueryService.findTrashed(req);
    }

    @Query
    @Description("Find reviews by product ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<ReviewRelationsDetailResponse>>> findReviewsByProduct(
            @Name("productId") Integer productId,
            @Name("request") FindAllReviewByProduct req) {
        req.setProductId(productId);
        return reviewQueryService.findByProduct(req);
    }

    @Query
    @Description("Find reviews by merchant ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<ReviewRelationsDetailResponse>>> findReviewsByMerchant(
            @Name("merchantId") Integer merchantId,
            @Name("request") FindAllReviewByMerchant req) {
        req.setMerchantId(merchantId);
        return reviewQueryService.findByMerchant(req);
    }

    @Query
    @Description("Find review by ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<ReviewResponse>> findReviewById(@Name("id") Integer reviewId) {
        return reviewQueryService.findById(reviewId);
    }

    @Mutation
    @Description("Create a new review")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<ReviewResponse>> createReview(@Name("request") CreateReviewRequest request) {
        return reviewCommandService.create(request);
    }

    @Mutation
    @Description("Update an existing review")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<ReviewResponse>> updateReview(@Name("id") Integer reviewId, @Name("request") UpdateReviewRequest request) {
        request.setReviewId(reviewId);
        return reviewCommandService.update(request);
    }

    @Mutation
    @Description("Trash a review by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<ReviewResponseDeleteAt>> trashReview(@Name("id") Integer reviewId) {
        return reviewCommandService.trash(reviewId);
    }

    @Mutation
    @Description("Restore a trashed review by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<ReviewResponseDeleteAt>> restoreReview(@Name("id") Integer reviewId) {
        return reviewCommandService.restore(reviewId);
    }

    @Mutation
    @Description("Permanently delete a review by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteReviewPermanent(@Name("id") Integer reviewId) {
        return reviewCommandService.delete(reviewId);
    }

    @Mutation
    @Description("Restore all trashed reviews")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> restoreAllReviews() {
        return reviewCommandService.restoreAll();
    }

    @Mutation
    @Description("Permanently delete all trashed reviews")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteAllReviewsPermanent() {
        return reviewCommandService.deleteAll();
    }
}
