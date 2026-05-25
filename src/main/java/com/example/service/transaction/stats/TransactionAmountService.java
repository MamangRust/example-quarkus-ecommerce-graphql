package com.example.service.transaction.stats;

import java.util.List;

import com.example.domain.requests.transactions.MonthAmountTransactionRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.transaction.TransactionMonthlyAmountFailedResponse;
import com.example.domain.response.transaction.TransactionMonthlyAmountSuccessResponse;
import com.example.domain.response.transaction.TransactionYearlyAmountFailedResponse;
import com.example.domain.response.transaction.TransactionYearlyAmountSuccessResponse;

import io.smallrye.mutiny.Uni;

public interface TransactionAmountService {
    Uni<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>> findMonthlyAmountSuccess(
            MonthAmountTransactionRequest req);

    Uni<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>> findYearlyAmountSuccess(Integer year);

    Uni<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>> findMonthlyAmountFailed(
            MonthAmountTransactionRequest req);

    Uni<ApiResponse<List<TransactionYearlyAmountFailedResponse>>> findYearlyAmountFailed(Integer year);
}
