package com.example.domain.requests.order;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class YearOrderMerchantRequest {

    @NotNull(message = "ID merchant wajib diisi")
    @QueryParam("merchantId")
    private Integer merchantId;

    @NotNull(message = "Tahun wajib diisi")
    @QueryParam("year")
    private Integer year;
}
