package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.cart.CreateCartRequest;
import com.example.domain.requests.cart.DeleteCartRequest;
import com.example.domain.requests.cart.FindAllCartsRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.cart.CartResponse;
import com.example.service.CartService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class CartGraphQL {

    @Inject
    CartService cartService;

    @Query
    @Description("Find all carts")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<CartResponse>>> findAllCarts(@Name("request") FindAllCartsRequest req) {
        return cartService.findAll(req);
    }

    @Mutation
    @Description("Create a new cart item")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<CartResponse>> createCart(@Name("request") CreateCartRequest req) {
        return cartService.createCart(req);
    }

    @Mutation
    @Description("Delete a cart item permanently by ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<Void>> deleteCart(@Name("id") Long id) {
        return cartService.deletePermanent(id);
    }

    @Mutation
    @Description("Delete all cart items permanently")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<Void>> deleteAllCarts(@Name("request") DeleteCartRequest req) {
        return cartService.deleteAllPermanently(req);
    }
}
