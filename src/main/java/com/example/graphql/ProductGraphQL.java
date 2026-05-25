package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.product.*;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.product.ProductResponse;
import com.example.domain.response.product.ProductResponseDeleteAt;
import com.example.service.product.ProductCommandService;
import com.example.service.product.ProductQueryService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class ProductGraphQL {

    @Inject
    ProductQueryService queryService;

    @Inject
    ProductCommandService commandService;

    @Query
    @Description("Find all products")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<ProductResponse>>> findAllProducts(@Name("request") FindAllProductRequest req) {
        return queryService.findAll(req);
    }

    @Query
    @Description("Find product by ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<ProductResponse>> findProductById(@Name("id") Long id) {
        return queryService.findById(id);
    }

    @Query
    @Description("Find products by merchant ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<ProductResponse>>> findProductsByMerchant(
            @Name("merchantId") Integer merchantId,
            @Name("request") FindAllProductByMerchantRequest req) {
        req.setMerchantId(merchantId);
        return queryService.findByMerchant(req);
    }

    @Query
    @Description("Find products by category name")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<ProductResponse>>> findProductsByCategory(
            @Name("categoryName") String categoryName,
            @Name("request") FindAllProductByCategoryRequest req) {
        req.setCategoryName(categoryName);
        return queryService.findByCategoryName(req);
    }

    @Query
    @Description("Find active products")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<ProductResponseDeleteAt>>> findActiveProducts(@Name("request") FindAllProductRequest req) {
        return queryService.findActiveProducts(req);
    }

    @Query
    @Description("Find trashed products")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<ProductResponseDeleteAt>>> findTrashedProducts(@Name("request") FindAllProductRequest req) {
        return queryService.findTrashedProducts(req);
    }

    @Mutation
    @Description("Create a new product")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<ProductResponse>> createProduct(@Name("request") CreateProductRequest req) {
        return commandService.createProduct(req);
    }

    @Mutation
    @Description("Update an existing product")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<ProductResponse>> updateProduct(@Name("id") Integer id, @Name("request") UpdateProductRequest req) {
        req.setProductId(id);
        return commandService.updateProduct(req);
    }

    @Mutation
    @Description("Trash a product by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<ProductResponseDeleteAt>> trashProduct(@Name("id") Integer id) {
        return commandService.trashedProduct(id);
    }

    @Mutation
    @Description("Restore a trashed product by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<ProductResponseDeleteAt>> restoreProduct(@Name("id") Integer id) {
        return commandService.restoreProduct(id);
    }

    @Mutation
    @Description("Permanently delete a product by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteProductPermanent(@Name("id") Integer id) {
        return commandService.deleteProductPermanent(id);
    }

    @Mutation
    @Description("Restore all trashed products")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> restoreAllProducts() {
        return commandService.restoreAllProducts();
    }

    @Mutation
    @Description("Permanently delete all trashed products")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteAllProductsPermanent() {
        return commandService.deleteAllProductsPermanent();
    }
}
