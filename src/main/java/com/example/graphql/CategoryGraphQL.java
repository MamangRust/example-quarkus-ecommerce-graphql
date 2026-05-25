package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.category.*;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.category.*;
import com.example.service.category.CategoryCommandService;
import com.example.service.category.CategoryQueryService;
import com.example.service.category.stats.price.CategoryPriceByIdService;
import com.example.service.category.stats.price.CategoryPriceByMerchantService;
import com.example.service.category.stats.price.CategoryPriceService;
import com.example.service.category.stats.totalprice.CategoryTotalPriceByIdService;
import com.example.service.category.stats.totalprice.CategoryTotalPriceByMerchantService;
import com.example.service.category.stats.totalprice.CategoryTotalPriceService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class CategoryGraphQL {

    @Inject
    CategoryQueryService categoryQueryService;

    @Inject
    CategoryCommandService categoryCommandService;

    @Inject
    CategoryTotalPriceService categoryTotalPriceService;

    @Inject
    CategoryTotalPriceByMerchantService categoryTotalPriceByMerchantService;

    @Inject
    CategoryTotalPriceByIdService categoryTotalPriceByIdService;

    @Inject
    CategoryPriceService categoryPriceService;

    @Inject
    CategoryPriceByMerchantService categoryPriceByMerchantService;

    @Inject
    CategoryPriceByIdService categoryPriceByIdService;

    @Query
    @Description("Find all categories")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<CategoryResponse>>> findAllCategories(@Name("request") FindAllCategoryRequest req) {
        return categoryQueryService.findAll(req);
    }

    @Query
    @Description("Find active categories")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<CategoryResponseDeleteAt>>> findActiveCategories(@Name("request") FindAllCategoryRequest req) {
        return categoryQueryService.findByActive(req);
    }

    @Query
    @Description("Find trashed categories")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<CategoryResponseDeleteAt>>> findTrashedCategories(@Name("request") FindAllCategoryRequest req) {
        return categoryQueryService.findByTrashed(req);
    }

    @Query
    @Description("Find category by ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<CategoryResponse>> findCategoryById(@Name("id") Long id) {
        return categoryQueryService.findById(id);
    }

    @Query
    @Description("Find monthly total pricing for categories")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthTotalPrice(@Name("request") MonthTotalPriceRequest req) {
        return categoryTotalPriceService.findMonthlyTotalPrice(req);
    }

    @Query
    @Description("Find yearly total pricing for categories")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearTotalPrice(@Name("year") Integer year) {
        return categoryTotalPriceService.findYearlyTotalPrice(year);
    }

    @Query
    @Description("Find monthly total pricing for categories by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthTotalPriceByMerchant(@Name("request") MonthTotalPriceMerchantRequest req) {
        return categoryTotalPriceByMerchantService.findMonthlyTotalPriceByMerchant(req);
    }

    @Query
    @Description("Find yearly total pricing for categories by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearTotalPriceByMerchant(@Name("request") YearTotalPriceMerchantRequest req) {
        return categoryTotalPriceByMerchantService.findYearlyTotalPriceByMerchant(req);
    }

    @Query
    @Description("Find monthly total pricing for categories by category ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthTotalPriceById(@Name("request") MonthTotalPriceIdRequest req) {
        return categoryTotalPriceByIdService.findMonthlyTotalPriceById(req);
    }

    @Query
    @Description("Find yearly total pricing for categories by category ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearTotalPriceById(@Name("request") YearTotalPriceIdRequest req) {
        return categoryTotalPriceByIdService.findYearlyTotalPriceById(req);
    }

    @Query
    @Description("Find monthly pricing for categories")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPrice(@Name("year") Integer year) {
        return categoryPriceService.findMonthPrice(year);
    }

    @Query
    @Description("Find yearly pricing for categories")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPrice(@Name("year") Integer year) {
        return categoryPriceService.findYearPrice(year);
    }

    @Query
    @Description("Find monthly pricing for categories by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceByMerchant(@Name("request") MonthPriceMerchantRequest req) {
        return categoryPriceByMerchantService.findMonthPriceByMerchant(req);
    }

    @Query
    @Description("Find yearly pricing for categories by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceByMerchant(@Name("request") YearPriceMerchantRequest req) {
        return categoryPriceByMerchantService.findYearPriceByMerchant(req);
    }

    @Query
    @Description("Find monthly pricing for categories by category ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceById(@Name("request") MonthPriceIdRequest req) {
        return categoryPriceByIdService.findMonthPriceById(req);
    }

    @Query
    @Description("Find yearly pricing for categories by category ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceById(@Name("request") YearPriceIdRequest req) {
        return categoryPriceByIdService.findYearPriceById(req);
    }

    @Mutation
    @Description("Create a new category")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<CategoryResponse>> createCategory(@Name("request") CreateCategoryRequest req) {
        return categoryCommandService.createCategory(req);
    }

    @Mutation
    @Description("Update an existing category")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<CategoryResponse>> updateCategory(@Name("id") Long id, @Name("request") UpdateCategoryRequest req) {
        req.setCategoryId(id.intValue());
        return categoryCommandService.updateCategory(req);
    }

    @Mutation
    @Description("Trash a category by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<CategoryResponseDeleteAt>> trashedCategory(@Name("id") Long id) {
        return categoryCommandService.trashedCategory(id);
    }

    @Mutation
    @Description("Restore a trashed category by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<CategoryResponseDeleteAt>> restoreCategory(@Name("id") Long id) {
        return categoryCommandService.restoreCategory(id);
    }

    @Mutation
    @Description("Permanently delete a category by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Void>> deleteCategoryPermanent(@Name("id") Long id) {
        return categoryCommandService.deleteCategoryPermanent(id);
    }

    @Mutation
    @Description("Restore all trashed categories")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Void>> restoreAllCategories() {
        return categoryCommandService.restoreAllCategories();
    }

    @Mutation
    @Description("Permanently delete all trashed categories")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Void>> deleteAllCategoriesPermanent() {
        return categoryCommandService.deleteAllCategoriesPermanent();
    }
}
