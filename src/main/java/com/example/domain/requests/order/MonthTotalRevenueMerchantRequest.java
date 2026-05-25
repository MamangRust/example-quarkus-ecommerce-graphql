package com.example.domain.requests.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class MonthTotalRevenueMerchantRequest {

    @NotNull(message = "ID merchant wajib diisi")
    @QueryParam("merchantId")
    private Integer merchantId;

    @NotNull(message = "Tahun wajib diisi")
    @Min(value = 1900, message = "Tahun harus valid")
    @QueryParam("year")
    private Integer year;

    @NotNull(message = "Bulan wajib diisi")
    @Min(value = 1, message = "Bulan harus antara 1–12")
    @Max(value = 12, message = "Bulan harus antara 1–12")
    @QueryParam("month")
    private Integer month;
}
