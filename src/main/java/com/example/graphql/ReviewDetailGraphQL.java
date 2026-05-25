package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;

import com.example.domain.requests.reviewdetail.CreateReviewDetailRequest;
import com.example.domain.requests.reviewdetail.UpdateReviewDetailRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.reviewdetail.ReviewDetailResponse;
import com.example.domain.response.reviewdetail.ReviewDetailResponseDeleteAt;
import com.example.service.reviewdetail.ReviewDetailService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class ReviewDetailGraphQL {

    @Inject
    ReviewDetailService reviewDetailService;

    @Mutation
    @Description("Create new review details")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<List<ReviewDetailResponse>>> createReviewDetails(@Name("requests") List<CreateReviewDetailRequest> requests) {
        return reviewDetailService.create(requests);
    }

    @Mutation
    @Description("Update existing review details")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<List<ReviewDetailResponse>>> updateReviewDetails(@Name("requests") List<UpdateReviewDetailRequest> requests) {
        return reviewDetailService.update(requests);
    }

    @Mutation
    @Description("Trash a review detail by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<ReviewDetailResponseDeleteAt>> trashReviewDetail(@Name("id") Integer id) {
        return reviewDetailService.trash(id);
    }

    @Mutation
    @Description("Restore a trashed review detail by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<ReviewDetailResponseDeleteAt>> restoreReviewDetail(@Name("id") Integer id) {
        return reviewDetailService.restore(id);
    }

    @Mutation
    @Description("Permanently delete a review detail by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteReviewDetailPermanent(@Name("id") Integer id) {
        return reviewDetailService.delete(id);
    }

    @Mutation
    @Description("Restore all trashed review details")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> restoreAllReviewDetails() {
        return reviewDetailService.restoreAll();
    }

    @Mutation
    @Description("Permanently delete all trashed review details")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteAllReviewDetailsPermanent() {
        return reviewDetailService.deleteAll();
    }
}
