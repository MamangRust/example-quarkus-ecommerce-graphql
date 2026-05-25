package com.example.service.transaction.statsbymerchant;

import java.util.List;

import com.example.domain.requests.transactions.MonthAmountTransactionMerchant;
import com.example.domain.requests.transactions.YearAmountTransactionMerchant;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.transaction.TransactionMonthlyAmountFailedResponse;
import com.example.domain.response.transaction.TransactionMonthlyAmountSuccessResponse;
import com.example.domain.response.transaction.TransactionYearlyAmountFailedResponse;
import com.example.domain.response.transaction.TransactionYearlyAmountSuccessResponse;

import io.smallrye.mutiny.Uni;

public interface TransactionAmountByMerchantService {
    Uni<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>> findMonthlyAmountSuccessByMerchant(
            MonthAmountTransactionMerchant req);

    Uni<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>> findYearlyAmountSuccessByMerchant(
            YearAmountTransactionMerchant req);

    Uni<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>> findMonthlyAmountFailedByMerchant(
            MonthAmountTransactionMerchant req);

    Uni<ApiResponse<List<TransactionYearlyAmountFailedResponse>>> findYearlyAmountFailedByMerchant(
            YearAmountTransactionMerchant req);
}
