package com.example.domain.requests.order;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class MonthTotalRevenue {
    @NotNull
    @QueryParam("year")
    private Integer year;

    @NotNull
    @QueryParam("month")
    private Integer month;
}
