package com.example.graphql;

import java.util.List;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import com.example.domain.requests.transactions.*;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.transaction.*;
import com.example.service.transaction.TransactionCommandService;
import com.example.service.transaction.TransactionQueryService;
import com.example.service.transaction.stats.TransactionAmountService;
import com.example.service.transaction.stats.TransactionMethodService;
import com.example.service.transaction.statsbymerchant.TransactionAmountByMerchantService;
import com.example.service.transaction.statsbymerchant.TransactionMethodByMerchantService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

@GraphQLApi
public class TransactionGraphQL {

    @Inject
    TransactionQueryService transactionQueryService;

    @Inject
    TransactionCommandService transactionCommandService;

    @Inject
    TransactionAmountService transactionAmountService;

    @Inject
    TransactionMethodService transactionMethodService;

    @Inject
    TransactionAmountByMerchantService transactionAmountByMerchantService;

    @Inject
    TransactionMethodByMerchantService transactionMethodByMerchantService;

    @Query
    @Description("Find all transactions")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponsePagination<List<TransactionResponse>>> findAllTransactions(@Name("request") FindAllTransactionRequest req) {
        return transactionQueryService.findAllTransactions(req);
    }

    @Query
    @Description("Find active transactions")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<TransactionResponseDeleteAt>>> findActiveTransactions(@Name("request") FindAllTransactionRequest req) {
        return transactionQueryService.findByActive(req);
    }

    @Query
    @Description("Find trashed transactions")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<TransactionResponseDeleteAt>>> findTrashedTransactions(@Name("request") FindAllTransactionRequest req) {
        return transactionQueryService.findByTrashed(req);
    }

    @Query
    @Description("Find transaction by ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<TransactionResponse>> findTransactionById(@Name("id") Integer id) {
        return transactionQueryService.findById(id);
    }

    @Query
    @Description("Find transactions by merchant ID")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponsePagination<List<TransactionResponse>>> findTransactionsByMerchant(
            @Name("merchantId") Integer merchantId,
            @Name("request") FindAllTransactionByMerchantRequest req) {
        req.setMerchantId(merchantId);
        return transactionQueryService.findByMerchant(req);
    }

    @Query
    @Description("Get monthly success transaction amount")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>> getMonthlyAmountSuccess(@Name("request") MonthAmountTransactionRequest req) {
        return transactionAmountService.findMonthlyAmountSuccess(req);
    }

    @Query
    @Description("Get yearly success transaction amount")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>> getYearlyAmountSuccess(@Name("year") Integer year) {
        return transactionAmountService.findYearlyAmountSuccess(year);
    }

    @Query
    @Description("Get monthly failed transaction amount")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>> getMonthlyAmountFailed(@Name("request") MonthAmountTransactionRequest req) {
        return transactionAmountService.findMonthlyAmountFailed(req);
    }

    @Query
    @Description("Get yearly failed transaction amount")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionYearlyAmountFailedResponse>>> getYearlyAmountFailed(@Name("year") Integer year) {
        return transactionAmountService.findYearlyAmountFailed(year);
    }

    @Query
    @Description("Get monthly success transaction amount by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>> getMonthlyAmountSuccessByMerchant(@Name("request") MonthAmountTransactionMerchant req) {
        return transactionAmountByMerchantService.findMonthlyAmountSuccessByMerchant(req);
    }

    @Query
    @Description("Get yearly success transaction amount by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>> getYearlyAmountSuccessByMerchant(@Name("request") YearAmountTransactionMerchant req) {
        return transactionAmountByMerchantService.findYearlyAmountSuccessByMerchant(req);
    }

    @Query
    @Description("Get monthly failed transaction amount by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>> getMonthlyAmountFailedByMerchant(@Name("request") MonthAmountTransactionMerchant req) {
        return transactionAmountByMerchantService.findMonthlyAmountFailedByMerchant(req);
    }

    @Query
    @Description("Get yearly failed transaction amount by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionYearlyAmountFailedResponse>>> getYearlyAmountFailedByMerchant(@Name("request") YearAmountTransactionMerchant req) {
        return transactionAmountByMerchantService.findYearlyAmountFailedByMerchant(req);
    }

    @Query
    @Description("Get monthly transaction method success")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> getMonthlyMethodSuccess(@Name("request") MonthMethodTransactionRequest req) {
        return transactionMethodService.findMonthlyMethodSuccess(req);
    }

    @Query
    @Description("Get yearly transaction method success")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> getYearlyMethodSuccess(@Name("year") Integer year) {
        return transactionMethodService.findYearlyMethodSuccess(year);
    }

    @Query
    @Description("Get monthly transaction method failed")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> getMonthlyMethodFailed(@Name("request") MonthMethodTransactionRequest req) {
        return transactionMethodService.findMonthlyMethodFailed(req);
    }

    @Query
    @Description("Get yearly transaction method failed")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> getYearlyMethodFailed(@Name("year") Integer year) {
        return transactionMethodService.findYearlyMethodFailed(year);
    }

    @Query
    @Description("Get monthly transaction method success by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> getMonthlyMethodByMerchantSuccess(
            @Name("request") MonthMethodTransactionMerchantRequest req) {
        return transactionMethodByMerchantService.findMonthlyMethodByMerchantSuccess(req);
    }

    @Query
    @Description("Get yearly transaction method success by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> getYearlyMethodByMerchantSuccess(@Name("request") YearMethodTransactionMerchantRequest req) {
        return transactionMethodByMerchantService.findYearlyMethodByMerchantSuccess(req);
    }

    @Query
    @Description("Get monthly transaction method failed by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> getMonthlyMethodByMerchantFailed(@Name("request") MonthMethodTransactionMerchantRequest req) {
        return transactionMethodByMerchantService.findMonthlyMethodByMerchantFailed(req);
    }

    @Query
    @Description("Get yearly transaction method failed by merchant")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF" })
    public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> getYearlyMethodByMerchantFailed(@Name("request") YearMethodTransactionMerchantRequest req) {
        return transactionMethodByMerchantService.findYearlyMethodByMerchantFailed(req);
    }

    @Mutation
    @Description("Create a new transaction")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    public Uni<ApiResponse<TransactionResponse>> createTransaction(@Name("request") CreateTransactionRequest request) {
        return transactionCommandService.create(request);
    }

    @Mutation
    @Description("Update an existing transaction")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<TransactionResponse>> updateTransaction(@Name("id") Integer id, @Name("request") UpdateTransactionRequest request) {
        request.setTransactionID(id);
        return transactionCommandService.update(request);
    }

    @Mutation
    @Description("Trash a transaction by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<TransactionResponseDeleteAt>> trashTransaction(@Name("id") Integer id) {
        return transactionCommandService.trash(id);
    }

    @Mutation
    @Description("Restore a trashed transaction by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<TransactionResponseDeleteAt>> restoreTransaction(@Name("id") Integer id) {
        return transactionCommandService.restore(id);
    }

    @Mutation
    @Description("Permanently delete a transaction by ID")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteTransactionPermanent(@Name("id") Integer id) {
        return transactionCommandService.delete(id);
    }

    @Mutation
    @Description("Restore all trashed transactions")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> restoreAllTransactions() {
        return transactionCommandService.restoreAll();
    }

    @Mutation
    @Description("Permanently delete all trashed transactions")
    @RolesAllowed("ROLE_ADMIN")
    public Uni<ApiResponse<Boolean>> deleteAllTransactionsPermanent() {
        return transactionCommandService.deleteAll();
    }
}
