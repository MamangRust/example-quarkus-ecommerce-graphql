package com.example.domain.requests.transactions;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTransactionRequest {

    @NotNull
    private Integer orderID;

    @NotNull
    private Integer merchantID;

    @NotNull
    private String paymentMethod;

    @NotNull
    @Min(0)
    private Integer amount;

    private String paymentStatus;
}
