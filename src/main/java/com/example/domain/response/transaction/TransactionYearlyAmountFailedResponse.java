package com.example.domain.response.transaction;

import java.util.List;

import com.example.entity.transaction.TransactionYearlyAmountFailed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionYearlyAmountFailedResponse {
    private String year;
    private Long totalFailed;
    private Long totalAmount;

    public static TransactionYearlyAmountFailedResponse from(TransactionYearlyAmountFailed response) {
        return TransactionYearlyAmountFailedResponse.builder()
                .year(response.getYear())
                .totalFailed(response.getTotalFailed())
                .totalAmount(response.getTotalAmount())
                .build();
    }

    public static List<TransactionYearlyAmountFailedResponse> fromList(List<TransactionYearlyAmountFailed> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(TransactionYearlyAmountFailedResponse::from).toList();
    }
}