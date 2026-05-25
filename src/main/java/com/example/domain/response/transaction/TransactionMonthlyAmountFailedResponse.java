package com.example.domain.response.transaction;

import java.util.List;

import com.example.entity.transaction.TransactionMonthlyAmountFailed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMonthlyAmountFailedResponse {
    private String year;
    private String month;
    private Long totalFailed;
    private Long totalAmount;

    public static TransactionMonthlyAmountFailedResponse from(TransactionMonthlyAmountFailed response) {
        return TransactionMonthlyAmountFailedResponse.builder()
                .year(response.getYear())
                .month(response.getMonth())
                .totalFailed(response.getTotalFailed())
                .totalAmount(response.getTotalAmount())
                .build();
    }

    public static List<TransactionMonthlyAmountFailedResponse> fromList(
            List<TransactionMonthlyAmountFailed> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(TransactionMonthlyAmountFailedResponse::from).toList();
    }
}