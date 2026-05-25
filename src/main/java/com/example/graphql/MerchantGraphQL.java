package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.merchant.CreateMerchantRequest;
import com.example.domain.requests.merchant.FindAllMerchantRequest;
import com.example.domain.requests.merchant.UpdateMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.merchant.MerchantResponse;
import com.example.domain.response.merchant.MerchantResponseDeleteAt;
import com.example.service.merchant.MerchantCommandService;
import com.example.service.merchant.MerchantQueryService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class MerchantGraphQL {

    @Inject
    MerchantQueryService merchantQueryService;

    @Inject
    MerchantCommandService merchantCommandService;

    @Query
    @Description("Find all merchants")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<MerchantResponse>>> findAllMerchants(@Name("request") FindAllMerchantRequest req) {
        return merchantQueryService.findAll(req);
    }

    @Query
    @Description("Find active merchants")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponsePagination<List<MerchantResponseDeleteAt>>> findActiveMerchants(@Name("request") FindAllMerchantRequest req) {
        return merchantQueryService.findByActive(req);
    }

    @Query
    @Description("Find trashed merchants")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponsePagination<List<MerchantResponseDeleteAt>>> findTrashedMerchants(@Name("request") FindAllMerchantRequest req) {
        return merchantQueryService.findByTrashed(req);
    }

    @Query
    @Description("Find merchant by ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<MerchantResponse>> findMerchantById(@Name("id") Long id) {
        return merchantQueryService.findById(id);
    }

    @Mutation
    @Description("Create a new merchant")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantResponse>> createMerchant(@Name("request") CreateMerchantRequest req) {
        return merchantCommandService.createMerchant(req);
    }

    @Mutation
    @Description("Update an existing merchant")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantResponse>> updateMerchant(@Name("id") Long id, @Name("request") UpdateMerchantRequest req) {
        req.setMerchantId(id.intValue());
        return merchantCommandService.updateMerchant(req);
    }

    @Mutation
    @Description("Trash a merchant by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantResponseDeleteAt>> trashMerchant(@Name("id") Long id) {
        return merchantCommandService.trashedMerchant(id);
    }

    @Mutation
    @Description("Restore a trashed merchant by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantResponseDeleteAt>> restoreMerchant(@Name("id") Long id) {
        return merchantCommandService.restoreMerchant(id);
    }

    @Mutation
    @Description("Permanently delete a merchant by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteMerchantPermanent(@Name("id") Long id) {
        return merchantCommandService.deleteMerchantPermanent(id);
    }

    @Mutation
    @Description("Restore all trashed merchants")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> restoreAllMerchants() {
        return merchantCommandService.restoreAllMerchant();
    }

    @Mutation
    @Description("Permanently delete all trashed merchants")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteAllMerchantsPermanent() {
        return merchantCommandService.deleteAllMerchantPermanent();
    }
}
