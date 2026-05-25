package com.example.domain.requests.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class YearTotalRevenueMerchantRequest {

    @NotNull(message = "ID merchant wajib diisi")
    @QueryParam("merchantId")
    private Integer merchantId;

    @NotNull(message = "Tahun wajib diisi")
    @Min(value = 1900, message = "Tahun harus valid")
    @QueryParam("year")
    private Integer year;
}
