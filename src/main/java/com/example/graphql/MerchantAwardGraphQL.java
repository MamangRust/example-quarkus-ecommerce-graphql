package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.merchant.FindAllMerchantRequest;
import com.example.domain.requests.merchantawrd.CreateMerchantAwardRequest;
import com.example.domain.requests.merchantawrd.UpdateMerchantAwardRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.merchantaward.MerchantAwardResponse;
import com.example.domain.response.merchantaward.MerchantAwardResponseDeleteAt;
import com.example.service.merchantaward.MerchantAwardCommandService;
import com.example.service.merchantaward.MerchantAwardQueryService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class MerchantAwardGraphQL {

    @Inject
    MerchantAwardQueryService queryService;

    @Inject
    MerchantAwardCommandService commandService;

    @Query
    @Description("Find all merchant awards")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<MerchantAwardResponse>>> findAllMerchantAwards(@Name("request") FindAllMerchantRequest req) {
        return queryService.findAll(req);
    }

    @Query
    @Description("Find merchant award by ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<MerchantAwardResponse>> findMerchantAwardById(@Name("id") Long id) {
        return queryService.findById(id);
    }

    @Query
    @Description("Find active merchant awards")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponsePagination<List<MerchantAwardResponseDeleteAt>>> findActiveMerchantAwards(@Name("request") FindAllMerchantRequest req) {
        return queryService.findByActive(req);
    }

    @Query
    @Description("Find trashed merchant awards")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponsePagination<List<MerchantAwardResponseDeleteAt>>> findTrashedMerchantAwards(@Name("request") FindAllMerchantRequest req) {
        return queryService.findByTrashed(req);
    }

    @Mutation
    @Description("Create a new merchant award")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantAwardResponse>> createMerchantAward(@Name("request") CreateMerchantAwardRequest req) {
        return commandService.createMerchantAward(req);
    }

    @Mutation
    @Description("Update an existing merchant award")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantAwardResponse>> updateMerchantAward(@Name("id") Long id, @Name("request") UpdateMerchantAwardRequest req) {
        req.setMerchantCertificationId(id.intValue());
        return commandService.updateMerchantAward(req);
    }

    @Mutation
    @Description("Trash a merchant award by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantAwardResponseDeleteAt>> trashMerchantAward(@Name("id") Long id) {
        return commandService.trashedMerchantAward(id);
    }

    @Mutation
    @Description("Restore a trashed merchant award by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantAwardResponseDeleteAt>> restoreMerchantAward(@Name("id") Long id) {
        return commandService.restoreMerchantAward(id);
    }

    @Mutation
    @Description("Permanently delete a merchant award by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteMerchantAwardPermanent(@Name("id") Long id) {
        return commandService.deleteMerchantAwardPermanent(id);
    }

    @Mutation
    @Description("Restore all trashed merchant awards")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> restoreAllMerchantAwards() {
        return commandService.restoreAllMerchantAward();
    }

    @Mutation
    @Description("Permanently delete all trashed merchant awards")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteAllMerchantAwardsPermanent() {
        return commandService.deleteAllMerchantAwardPermanent();
    }
}
