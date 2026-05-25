package com.example.service.transaction.stats;

import java.util.List;

import com.example.domain.requests.transactions.MonthMethodTransactionRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.transaction.TransactionMonthlyMethodResponse;
import com.example.domain.response.transaction.TransactionYearlyMethodResponse;

import io.smallrye.mutiny.Uni;

public interface TransactionMethodService {
    Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodSuccess(
            MonthMethodTransactionRequest req);

    Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodSuccess(Integer year);

    Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodFailed(MonthMethodTransactionRequest req);

    Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodFailed(Integer year);
}
