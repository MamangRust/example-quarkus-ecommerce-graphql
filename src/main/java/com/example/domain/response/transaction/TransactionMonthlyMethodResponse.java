package com.example.domain.response.transaction;

import java.util.List;

import com.example.entity.transaction.TransactionMonthlyMethod;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMonthlyMethodResponse {
    private String month;
    private String paymentMethod;
    private Long totalTransactions;
    private Long totalAmount;

    public static TransactionMonthlyMethodResponse from(TransactionMonthlyMethod response) {
        return TransactionMonthlyMethodResponse.builder()
                .month(response.getMonth())
                .paymentMethod(response.getPaymentMethod())
                .totalTransactions(response.getTotalTransactions())
                .totalAmount(response.getTotalAmount())
                .build();
    }

    public static List<TransactionMonthlyMethodResponse> fromList(List<TransactionMonthlyMethod> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(TransactionMonthlyMethodResponse::from).toList();
    }
}
