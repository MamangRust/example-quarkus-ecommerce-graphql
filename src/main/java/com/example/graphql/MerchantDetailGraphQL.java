package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.merchant.FindAllMerchantRequest;
import com.example.domain.requests.merchantdetail.CreateMerchantDetailRequest;
import com.example.domain.requests.merchantdetail.UpdateMerchantDetailRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.merchantdetail.MerchantDetailResponse;
import com.example.domain.response.merchantdetail.MerchantDetailResponseDeleteAt;
import com.example.domain.response.merchantdetail.MerchantDetailRelationResponse;
import com.example.domain.response.merchantdetail.MerchantDetailRelationResponseDeleteAt;
import com.example.service.merchantdetail.MerchantDetailCommandService;
import com.example.service.merchantdetail.MerchantDetailQueryService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class MerchantDetailGraphQL {

    @Inject
    MerchantDetailQueryService queryService;

    @Inject
    MerchantDetailCommandService commandService;

    @Query
    @Description("Find all merchant details")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<MerchantDetailRelationResponse>>> findAllMerchantDetails(@Name("request") FindAllMerchantRequest req) {
        return queryService.findAll(req);
    }

    @Query
    @Description("Find merchant detail by ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<MerchantDetailRelationResponse>> findMerchantDetailById(@Name("id") Long id) {
        return queryService.findById(id);
    }

    @Query
    @Description("Find active merchant details")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<MerchantDetailRelationResponseDeleteAt>>> findActiveMerchantDetails(@Name("request") FindAllMerchantRequest req) {
        return queryService.findByActive(req);
    }

    @Query
    @Description("Find trashed merchant details")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<MerchantDetailRelationResponseDeleteAt>>> findTrashedMerchantDetails(@Name("request") FindAllMerchantRequest req) {
        return queryService.findByTrashed(req);
    }

    @Mutation
    @Description("Create a new merchant detail")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantDetailResponse>> createMerchantDetail(@Name("request") CreateMerchantDetailRequest req) {
        return commandService.createMerchant(req);
    }

    @Mutation
    @Description("Update an existing merchant detail")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantDetailResponse>> updateMerchantDetail(@Name("id") Long id, @Name("request") UpdateMerchantDetailRequest req) {
        req.setMerchantDetailId(id.intValue());
        return commandService.updateMerchant(req);
    }

    @Mutation
    @Description("Trash a merchant detail by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantDetailResponseDeleteAt>> trashMerchantDetail(@Name("id") Long id) {
        return commandService.trashedMerchant(id);
    }

    @Mutation
    @Description("Restore a trashed merchant detail by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantDetailResponseDeleteAt>> restoreMerchantDetail(@Name("id") Long id) {
        return commandService.restoreMerchant(id);
    }

    @Mutation
    @Description("Permanently delete a merchant detail by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteMerchantDetailPermanent(@Name("id") Long id) {
        return commandService.deleteMerchantPermanent(id);
    }

    @Mutation
    @Description("Restore all trashed merchant details")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> restoreAllMerchantDetails() {
        return commandService.restoreAllMerchant();
    }

    @Mutation
    @Description("Permanently delete all trashed merchant details")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteAllMerchantDetailsPermanent() {
        return commandService.deleteAllMerchantPermanent();
    }
}
