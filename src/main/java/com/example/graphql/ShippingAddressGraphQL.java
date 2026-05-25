package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.shipping.FindAllShippingAddress;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.shipping.ShippingAddressResponse;
import com.example.domain.response.shipping.ShippingAddressResponseDeleteAt;
import com.example.service.shippingaddress.ShippingAddressCommand;
import com.example.service.shippingaddress.ShippingAddressQueryService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class ShippingAddressGraphQL {

    @Inject
    ShippingAddressQueryService queryService;

    @Inject
    ShippingAddressCommand commandService;

    @Query
    @Description("Find all shipping addresses")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<ShippingAddressResponse>>> findAllShippingAddresses(@Name("request") FindAllShippingAddress req) {
        return queryService.findAll(req);
    }

    @Query
    @Description("Find active shipping addresses")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<ShippingAddressResponseDeleteAt>>> findActiveShippingAddresses(@Name("request") FindAllShippingAddress req) {
        return queryService.findByActive(req);
    }

    @Query
    @Description("Find trashed shipping addresses")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<ShippingAddressResponseDeleteAt>>> findTrashedShippingAddresses(@Name("request") FindAllShippingAddress req) {
        return queryService.findByTrashed(req);
    }

    @Query
    @Description("Find shipping address by ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<ShippingAddressResponse>> findShippingAddressById(@Name("id") Integer id) {
        return queryService.findById(id);
    }

    @Query
    @Description("Find shipping address by order ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<ShippingAddressResponse>> findShippingAddressByOrder(@Name("orderId") Integer orderId) {
        return queryService.findByOrder(orderId);
    }

    @Mutation
    @Description("Trash a shipping address by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<ShippingAddressResponseDeleteAt>> trashShippingAddress(@Name("id") Integer id) {
        return commandService.trash(id);
    }

    @Mutation
    @Description("Restore a trashed shipping address by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<ShippingAddressResponseDeleteAt>> restoreShippingAddress(@Name("id") Integer id) {
        return commandService.restore(id);
    }

    @Mutation
    @Description("Permanently delete a shipping address by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteShippingAddressPermanent(@Name("id") Integer id) {
        return commandService.deletePermanently(id);
    }

    @Mutation
    @Description("Restore all trashed shipping addresses")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> restoreAllShippingAddresses() {
        return commandService.restoreAll();
    }

    @Mutation
    @Description("Permanently delete all trashed shipping addresses")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteAllShippingAddressesPermanent() {
        return commandService.deleteAllPermanent();
    }
}
