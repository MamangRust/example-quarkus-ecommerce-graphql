package com.example.domain.requests.transactions;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class MonthMethodTransactionRequest {

    @NotNull(message = "Tahun wajib diisi")
    @Min(value = 1900, message = "Tahun harus valid")
    @QueryParam("year")
    private Integer year;

    @NotNull(message = "Bulan wajib diisi")
    @Min(value = 1, message = "Bulan harus antara 1 sampai 12")
    @QueryParam("month")
    private Integer month;
}
