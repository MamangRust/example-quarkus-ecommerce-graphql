package com.example.service.transaction.statsbymerchant;

import java.util.List;

import com.example.domain.requests.transactions.MonthMethodTransactionMerchantRequest;
import com.example.domain.requests.transactions.YearMethodTransactionMerchantRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.transaction.TransactionMonthlyMethodResponse;
import com.example.domain.response.transaction.TransactionYearlyMethodResponse;

import io.smallrye.mutiny.Uni;

public interface TransactionMethodByMerchantService {
    Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodByMerchantSuccess(
            MonthMethodTransactionMerchantRequest req);

    Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodByMerchantFailed(
            MonthMethodTransactionMerchantRequest req);

    Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodByMerchantSuccess(
            YearMethodTransactionMerchantRequest req);

    Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodByMerchantFailed(
            YearMethodTransactionMerchantRequest req);
}
