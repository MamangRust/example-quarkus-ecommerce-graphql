package com.example.service.transaction;

import java.util.List;

import com.example.domain.requests.transactions.FindAllTransactionByMerchantRequest;
import com.example.domain.requests.transactions.FindAllTransactionRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.transaction.TransactionResponse;
import com.example.domain.response.transaction.TransactionResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface TransactionQueryService {
    Uni<ApiResponsePagination<List<TransactionResponse>>> findAllTransactions(FindAllTransactionRequest req);

    Uni<ApiResponsePagination<List<TransactionResponseDeleteAt>>> findByActive(FindAllTransactionRequest req);

    Uni<ApiResponsePagination<List<TransactionResponseDeleteAt>>> findByTrashed(FindAllTransactionRequest req);

    Uni<ApiResponsePagination<List<TransactionResponse>>> findByMerchant(FindAllTransactionByMerchantRequest req);

    Uni<ApiResponse<TransactionResponse>> findById(Integer id);

    Uni<ApiResponse<TransactionResponse>> findByOrderId(Integer id);
}
