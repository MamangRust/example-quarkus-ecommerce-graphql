package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.merchant.FindAllMerchantRequest;
import com.example.domain.requests.merchantbusiness.CreateMerchantBusinessRequest;
import com.example.domain.requests.merchantbusiness.UpdateMerchantBusinessRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.merchantbusiness.MerchantBusinessResponse;
import com.example.domain.response.merchantbusiness.MerchantBusinessResponseDeleteAt;
import com.example.service.merchantbusiness.MerchantBusinessCommandService;
import com.example.service.merchantbusiness.MerchantBusinessQueryService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class MerchantBusinessGraphQL {

    @Inject
    MerchantBusinessQueryService queryService;

    @Inject
    MerchantBusinessCommandService commandService;

    @Query
    @Description("Find all merchant businesses")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<MerchantBusinessResponse>>> findAllMerchantBusinesses(@Name("request") FindAllMerchantRequest req) {
        return queryService.findAll(req);
    }

    @Query
    @Description("Find merchant business by ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<MerchantBusinessResponse>> findMerchantBusinessById(@Name("id") Long id) {
        return queryService.findById(id);
    }

    @Query
    @Description("Find active merchant businesses")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponsePagination<List<MerchantBusinessResponseDeleteAt>>> findActiveMerchantBusinesses(@Name("request") FindAllMerchantRequest req) {
        return queryService.findByActive(req);
    }

    @Query
    @Description("Find trashed merchant businesses")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponsePagination<List<MerchantBusinessResponseDeleteAt>>> findTrashedMerchantBusinesses(@Name("request") FindAllMerchantRequest req) {
        return queryService.findByTrashed(req);
    }

    @Mutation
    @Description("Create a new merchant business")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantBusinessResponse>> createMerchantBusiness(@Name("request") CreateMerchantBusinessRequest req) {
        return commandService.createMerchantBusiness(req);
    }

    @Mutation
    @Description("Update an existing merchant business")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantBusinessResponse>> updateMerchantBusiness(@Name("id") Long id, @Name("request") UpdateMerchantBusinessRequest req) {
        req.setMerchantBusinessInfoId(id.intValue());
        return commandService.updateMerchantBusiness(req);
    }

    @Mutation
    @Description("Trash a merchant business by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantBusinessResponseDeleteAt>> trashMerchantBusiness(@Name("id") Long id) {
        return commandService.trashedMerchantBusiness(id);
    }

    @Mutation
    @Description("Restore a trashed merchant business by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantBusinessResponseDeleteAt>> restoreMerchantBusiness(@Name("id") Long id) {
        return commandService.restoreMerchantBusiness(id);
    }

    @Mutation
    @Description("Permanently delete a merchant business by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteMerchantBusinessPermanent(@Name("id") Long id) {
        return commandService.deleteMerchantBusinessPermanent(id);
    }

    @Mutation
    @Description("Restore all trashed merchant businesses")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> restoreAllMerchantBusinesses() {
        return commandService.restoreAllMerchantBusiness();
    }

    @Mutation
    @Description("Permanently delete all trashed merchant businesses")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteAllMerchantBusinessesPermanent() {
        return commandService.deleteAllMerchantBusinessPermanent();
    }
}
