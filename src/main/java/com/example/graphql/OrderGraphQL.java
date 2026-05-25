package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.order.*;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.order.*;
import com.example.service.order.OrderCommandService;
import com.example.service.order.OrderQueryService;
import com.example.service.order.stats.OrderSoldoutService;
import com.example.service.order.stats.OrderTotalRevenueService;
import com.example.service.order.statsbymerchant.OrderSoldOutByMerchantService;
import com.example.service.order.statsbymerchant.OrderTotalRevenueByMerchantService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class OrderGraphQL {

    @Inject
    OrderQueryService orderQueryService;

    @Inject
    OrderCommandService orderCommandService;

    @Inject
    OrderSoldoutService orderSoldoutService;

    @Inject
    OrderTotalRevenueService orderTotalRevenueService;

    @Inject
    OrderSoldOutByMerchantService orderSoldOutByMerchantService;

    @Inject
    OrderTotalRevenueByMerchantService orderTotalRevenueByMerchantService;

    @Query
    @Description("Find all orders")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<OrderResponse>>> findAllOrders(@Name("request") FindAllOrderRequest req) {
        return orderQueryService.findAll(req);
    }

    @Query
    @Description("Find order by ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<OrderResponse>> findOrderById(@Name("id") Long id) {
        return orderQueryService.findById(id);
    }

    @Query
    @Description("Find order relations by order ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<OrderRelationResponse>> findOrderRelation(@Name("id") Long id) {
        return orderQueryService.findOrderRelation(id);
    }

    @Query
    @Description("Find active orders")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponsePagination<List<OrderResponseDeleteAt>>> findActiveOrders(@Name("request") FindAllOrderRequest req) {
        return orderQueryService.findByActive(req);
    }

    @Query
    @Description("Find trashed orders")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponsePagination<List<OrderResponseDeleteAt>>> findTrashedOrders(@Name("request") FindAllOrderRequest req) {
        return orderQueryService.findByTrashed(req);
    }

    @Query
    @Description("Find orders by merchant ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<OrderResponse>>> findOrdersByMerchant(
            @Name("merchantId") Integer merchantId,
            @Name("request") FindAllOrderByMerchantRequest req) {
        req.setMerchantId(merchantId);
        return orderQueryService.findByMerchantId(req);
    }

    @Query
    @Description("Find monthly orders revenue")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<OrderMonthlyResponse>>> findMonthlyOrdersRevenue(@Name("yearMonth") Integer yearMonth) {
        return orderSoldoutService.findMonthlyOrders(yearMonth);
    }

    @Query
    @Description("Find yearly orders revenue")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<OrderYearlyResponse>>> findYearlyOrdersRevenue(@Name("year") Integer year) {
        return orderSoldoutService.findYearlyOrders(year);
    }

    @Query
    @Description("Find monthly total revenue stats")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> findMonthlyTotalRevenue(@Name("request") MonthTotalRevenue req) {
        return orderTotalRevenueService.findMonthlyStats(req);
    }

    @Query
    @Description("Find yearly total revenue stats")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> findYearlyTotalRevenue(@Name("year") Integer year) {
        return orderTotalRevenueService.findYearlyStats(year);
    }

    @Query
    @Description("Find monthly orders revenue by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<OrderMonthlyResponse>>> findMonthlyOrdersRevenueByMerchant(@Name("request") MonthOrderMerchantRequest req) {
        return orderSoldOutByMerchantService.findMonthlyOrdersByMerchant(req);
    }

    @Query
    @Description("Find yearly orders revenue by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<OrderYearlyResponse>>> findYearlyOrdersRevenueByMerchant(@Name("request") YearOrderMerchantRequest req) {
        return orderSoldOutByMerchantService.findYearlyOrdersByMerchant(req);
    }

    @Query
    @Description("Find monthly total revenue stats by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> findMonthlyTotalRevenueByMerchant(@Name("request") MonthTotalRevenueMerchantRequest req) {
        return orderTotalRevenueByMerchantService.findMonthlyStatsByMerchant(req);
    }

    @Query
    @Description("Find yearly total revenue stats by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> findYearlyTotalRevenueByMerchant(@Name("request") YearTotalRevenueMerchantRequest req) {
        return orderTotalRevenueByMerchantService.findYearlyStatsByMerchant(req);
    }

    @Mutation
    @Description("Create a new order")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<OrderResponse>> createOrder(@Name("request") CreateOrderRequest req) {
        return orderCommandService.create(req);
    }

    @Mutation
    @Description("Update an existing order")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<OrderResponse>> updateOrder(@Name("id") Long id, @Name("request") UpdateOrderRequest req) {
        req.setOrderId(id.intValue());
        return orderCommandService.update(req);
    }

    @Mutation
    @Description("Trash an order by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<OrderResponseDeleteAt>> trashOrder(@Name("id") Long id) {
        return orderCommandService.trash(id);
    }

    @Mutation
    @Description("Restore a trashed order by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<OrderResponseDeleteAt>> restoreOrder(@Name("id") Long id) {
        return orderCommandService.restore(id);
    }

    @Mutation
    @Description("Permanently delete an order by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteOrderPermanent(@Name("id") Long id) {
        return orderCommandService.delete(id);
    }

    @Mutation
    @Description("Restore all trashed orders")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> restoreAllOrders() {
        return orderCommandService.restoreAll();
    }

    @Mutation
    @Description("Permanently delete all trashed orders")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteAllOrdersPermanent() {
        return orderCommandService.deleteAll();
    }
}
