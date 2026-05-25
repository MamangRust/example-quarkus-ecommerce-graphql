package com.example.entity.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMonthlyMethod {
    private String month;
    private String paymentMethod;
    private Long totalTransactions;
    private Long totalAmount;
}