package com.example.domain.response.transaction;

import java.util.List;

import com.example.entity.transaction.TransactionYearlyAmountSuccess;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionYearlyAmountSuccessResponse {
    private String year;
    private Long totalSuccess;
    private Long totalAmount;

    public static TransactionYearlyAmountSuccessResponse from(TransactionYearlyAmountSuccess response) {
        return TransactionYearlyAmountSuccessResponse.builder()
                .year(response.getYear())
                .totalSuccess(response.getTotalSuccess())
                .totalAmount(response.getTotalAmount())
                .build();
    }

    public static List<TransactionYearlyAmountSuccessResponse> fromList(
            List<TransactionYearlyAmountSuccess> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(TransactionYearlyAmountSuccessResponse::from).toList();
    }
}