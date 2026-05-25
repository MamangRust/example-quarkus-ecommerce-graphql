package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.slider.CreateSliderRequest;
import com.example.domain.requests.slider.FindAllSliderRequest;
import com.example.domain.requests.slider.UpdateSliderRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.slider.SliderResponse;
import com.example.domain.response.slider.SliderResponseDeleteAt;
import com.example.service.slider.SliderCommandService;
import com.example.service.slider.SliderQueryService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class SliderGraphQL {

    @Inject
    SliderQueryService sliderQueryService;

    @Inject
    SliderCommandService sliderCommandService;

    @Query
    @Description("Find all sliders")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<SliderResponse>>> findAllSliders(@Name("request") FindAllSliderRequest req) {
        return sliderQueryService.findAll(req);
    }

    @Query
    @Description("Find active sliders")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<SliderResponseDeleteAt>>> findActiveSliders(@Name("request") FindAllSliderRequest req) {
        return sliderQueryService.findByActive(req);
    }

    @Query
    @Description("Find trashed sliders")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<SliderResponseDeleteAt>>> findTrashedSliders(@Name("request") FindAllSliderRequest req) {
        return sliderQueryService.findByTrashed(req);
    }

    @Mutation
    @Description("Create a new slider")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<SliderResponse>> createSlider(@Name("request") CreateSliderRequest req) {
        return sliderCommandService.createSlider(req);
    }

    @Mutation
    @Description("Update an existing slider")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<SliderResponse>> updateSlider(@Name("id") Integer id, @Name("request") UpdateSliderRequest req) {
        req.setId(id);
        return sliderCommandService.updateSlider(req);
    }

    @Mutation
    @Description("Trash a slider by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<SliderResponseDeleteAt>> trashSlider(@Name("id") Integer id) {
        return sliderCommandService.trashedSlider(id);
    }

    @Mutation
    @Description("Restore a trashed slider by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<SliderResponseDeleteAt>> restoreSlider(@Name("id") Integer id) {
        return sliderCommandService.restoreSlider(id);
    }

    @Mutation
    @Description("Permanently delete a slider by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteSliderPermanent(@Name("id") Integer id) {
        return sliderCommandService.deleteSliderPermanent(id);
    }

    @Mutation
    @Description("Restore all trashed sliders")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> restoreAllSliders() {
        return sliderCommandService.restoreAllSliders();
    }

    @Mutation
    @Description("Permanently delete all trashed sliders")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteAllSlidersPermanent() {
        return sliderCommandService.deleteAllSlidersPermanent();
    }
}
