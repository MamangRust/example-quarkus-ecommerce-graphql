package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.merchant.FindAllMerchantRequest;
import com.example.domain.requests.merchantpolicy.CreateMerchantPolicyRequest;
import com.example.domain.requests.merchantpolicy.UpdateMerchantPolicyRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.merchantpolicy.MerchantPoliciesResponse;
import com.example.domain.response.merchantpolicy.MerchantPoliciesResponseDeleteAt;
import com.example.service.merchantpolicy.MerchantPolicyCommandService;
import com.example.service.merchantpolicy.MerchantPolicyQueryService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class MerchantPolicyGraphQL {

    @Inject
    MerchantPolicyQueryService queryService;

    @Inject
    MerchantPolicyCommandService commandService;

    @Query
    @Description("Find all merchant policies")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<MerchantPoliciesResponse>>> findAllMerchantPolicies(@Name("request") FindAllMerchantRequest req) {
        return queryService.findAll(req);
    }

    @Query
    @Description("Find merchant policy by ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<MerchantPoliciesResponse>> findMerchantPolicyById(@Name("id") Long id) {
        return queryService.findById(id);
    }

    @Query
    @Description("Find active merchant policies")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<MerchantPoliciesResponseDeleteAt>>> findActiveMerchantPolicies(@Name("request") FindAllMerchantRequest req) {
        return queryService.findByActive(req);
    }

    @Query
    @Description("Find trashed merchant policies")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<MerchantPoliciesResponseDeleteAt>>> findTrashedMerchantPolicies(@Name("request") FindAllMerchantRequest req) {
        return queryService.findByTrashed(req);
    }

    @Mutation
    @Description("Create a new merchant policy")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantPoliciesResponse>> createMerchantPolicy(@Name("request") CreateMerchantPolicyRequest req) {
        return commandService.create(req);
    }

    @Mutation
    @Description("Update an existing merchant policy")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantPoliciesResponse>> updateMerchantPolicy(@Name("id") Long id, @Name("request") UpdateMerchantPolicyRequest req) {
        req.setMerchantPolicyId(id.intValue());
        return commandService.update(req);
    }

    @Mutation
    @Description("Trash a merchant policy by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantPoliciesResponseDeleteAt>> trashMerchantPolicy(@Name("id") Long id) {
        return commandService.trash(id);
    }

    @Mutation
    @Description("Restore a trashed merchant policy by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantPoliciesResponseDeleteAt>> restoreMerchantPolicy(@Name("id") Long id) {
        return commandService.restore(id);
    }

    @Mutation
    @Description("Permanently delete a merchant policy by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteMerchantPolicyPermanent(@Name("id") Long id) {
        return commandService.delete(id);
    }

    @Mutation
    @Description("Restore all trashed merchant policies")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> restoreAllMerchantPolicies() {
        return commandService.restoreAll();
    }

    @Mutation
    @Description("Permanently delete all trashed merchant policies")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteAllMerchantPoliciesPermanent() {
        return commandService.deleteAll();
    }
}
