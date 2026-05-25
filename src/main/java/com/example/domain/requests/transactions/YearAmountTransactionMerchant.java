package com.example.domain.requests.transactions;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class YearAmountTransactionMerchant {

    @NotNull
    @QueryParam("merchantId")
    private Integer merchantId;

    @NotNull
    @QueryParam("year")
    private Integer year;
}
