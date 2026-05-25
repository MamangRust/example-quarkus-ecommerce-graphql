package com.example.domain.response.transaction;

import java.util.List;

import com.example.entity.transaction.TransactionYearlyMethod;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionYearlyMethodResponse {
    private String year;
    private String paymentMethod;
    private Long totalTransactions;
    private Long totalAmount;

    public static TransactionYearlyMethodResponse from(TransactionYearlyMethod response) {
        return TransactionYearlyMethodResponse.builder()
                .year(response.getYear())
                .paymentMethod(response.getPaymentMethod())
                .totalTransactions(response.getTotalTransactions())
                .totalAmount(response.getTotalAmount())
                .build();
    }

    public static List<TransactionYearlyMethodResponse> fromList(List<TransactionYearlyMethod> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(TransactionYearlyMethodResponse::from).toList();
    }
}