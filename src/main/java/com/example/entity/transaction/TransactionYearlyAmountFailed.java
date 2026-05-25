package com.example.entity.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionYearlyAmountFailed {
    private String year;
    private Long totalFailed;
    private Long totalAmount;
}