package com.example.entity.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMonthlyAmountFailed {
    private String year;
    private String month;
    private Long totalFailed;
    private Long totalAmount;
}