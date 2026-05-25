package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.banner.CreateBannerRequest;
import com.example.domain.requests.banner.FindAllBannerRequest;
import com.example.domain.requests.banner.UpdateBannerRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.banner.BannerResponse;
import com.example.domain.response.banner.BannerResponseDeleteAt;
import com.example.service.banner.BannerCommandService;
import com.example.service.banner.BannerQueryService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class BannerGraphQL {

    @Inject
    BannerQueryService bannerQueryService;

    @Inject
    BannerCommandService bannerCommandService;

    @Query
    @Description("Find all banners paginated")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<BannerResponse>>> findAllBanners(@Name("request") FindAllBannerRequest req) {
        return bannerQueryService.findAll(req);
    }

    @Query
    @Description("Find active banners paginated")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<BannerResponseDeleteAt>>> findActiveBanners(@Name("request") FindAllBannerRequest req) {
        return bannerQueryService.findByActive(req);
    }

    @Query
    @Description("Find trashed banners paginated")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<BannerResponseDeleteAt>>> findTrashedBanners(@Name("request") FindAllBannerRequest req) {
        return bannerQueryService.findByTrashed(req);
    }

    @Query
    @Description("Find banner by ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<BannerResponse>> findBannerById(@Name("id") Long id) {
        return bannerQueryService.findById(id);
    }

    @Mutation
    @Description("Create a new banner")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<BannerResponse>> createBanner(@Name("request") CreateBannerRequest req) {
        return bannerCommandService.createBanner(req);
    }

    @Mutation
    @Description("Update an existing banner")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<BannerResponse>> updateBanner(@Name("id") Long id, @Name("request") UpdateBannerRequest req) {
        req.setBannerID(id.intValue());
        return bannerCommandService.updateBanner(req);
    }

    @Mutation
    @Description("Trash a banner by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<BannerResponseDeleteAt>> trashedBanner(@Name("id") Long id) {
        return bannerCommandService.trashedBanner(id);
    }

    @Mutation
    @Description("Restore a trashed banner by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<BannerResponseDeleteAt>> restoreBanner(@Name("id") Long id) {
        return bannerCommandService.restoreBanner(id);
    }

    @Mutation
    @Description("Permanently delete a banner by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Void>> deleteBannerPermanent(@Name("id") Long id) {
        return bannerCommandService.deleteBannerPermanent(id);
    }

    @Mutation
    @Description("Restore all trashed banners")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Void>> restoreAllBanner() {
        return bannerCommandService.restoreAllBanner();
    }

    @Mutation
    @Description("Permanently delete all trashed banners")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Void>> deleteAllBannerPermanent() {
        return bannerCommandService.deleteAllBannerPermanent();
    }
}
