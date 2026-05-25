package com.example.graphql;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;

import com.example.domain.requests.merchantsociallink.CreateMerchantSocialRequest;
import com.example.domain.requests.merchantsociallink.UpdateMerchantSocialRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.merchantsociallink.MerchantSocialMediaLinkResponse;
import com.example.domain.response.merchantsociallink.MerchantSocialMediaLinkResponseDeleteAt;
import com.example.service.MerchantSocialLinkService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class MerchantSocialLinkGraphQL {

    @Inject
    MerchantSocialLinkService service;

    @Mutation
    @Description("Create a new merchant social link")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantSocialMediaLinkResponse>> createMerchantSocialLink(@Name("request") CreateMerchantSocialRequest request) {
        return service.create(request);
    }

    @Mutation
    @Description("Update an existing merchant social link")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantSocialMediaLinkResponse>> updateMerchantSocialLink(@Name("id") Long id, @Name("request") UpdateMerchantSocialRequest request) {
        request.setId(id.intValue());
        return service.update(request);
    }

    @Mutation
    @Description("Trash a merchant social link by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantSocialMediaLinkResponseDeleteAt>> trashMerchantSocialLink(@Name("id") Integer id) {
        return service.trash(id);
    }

    @Mutation
    @Description("Restore a trashed merchant social link by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<MerchantSocialMediaLinkResponseDeleteAt>> restoreMerchantSocialLink(@Name("id") Integer id) {
        return service.restore(id);
    }

    @Mutation
    @Description("Permanently delete a merchant social link by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteMerchantSocialLinkPermanent(@Name("id") Integer id) {
        return service.delete(id);
    }

    @Mutation
    @Description("Restore all trashed merchant social links")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> restoreAllMerchantSocialLinks() {
        return service.restoreAll();
    }

    @Mutation
    @Description("Permanently delete all trashed merchant social links")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteAllMerchantSocialLinksPermanent() {
        return service.deleteAll();
    }
}
