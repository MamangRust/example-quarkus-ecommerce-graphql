package com.example.service.transaction;

import com.example.domain.requests.transactions.CreateTransactionRequest;
import com.example.domain.requests.transactions.UpdateTransactionRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.transaction.TransactionResponse;
import com.example.domain.response.transaction.TransactionResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface TransactionCommandService {
    Uni<ApiResponse<TransactionResponse>> create(CreateTransactionRequest req);

    Uni<ApiResponse<TransactionResponse>> update(UpdateTransactionRequest req);

    Uni<ApiResponse<TransactionResponseDeleteAt>> trash(Integer id);

    Uni<ApiResponse<TransactionResponseDeleteAt>> restore(Integer id);

    Uni<ApiResponse<Boolean>> delete(Integer id);

    Uni<ApiResponse<Boolean>> restoreAll();

    Uni<ApiResponse<Boolean>> deleteAll();
}
