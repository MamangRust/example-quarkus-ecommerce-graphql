package com.example.domain.requests.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class FindAllProductByCategoryRequest {

    @NotNull
    @QueryParam("categoryName")
    private String categoryName;

    @QueryParam("search")
    @DefaultValue("")
    private String search = "";

    @Min(0)
    @QueryParam("minPrice")
    @DefaultValue("0")
    private Integer minPrice = 0;

    @Min(0)
    @QueryParam("maxPrice")
    @DefaultValue("999999999")
    private Integer maxPrice = 999999999;

    @NotNull
    @Min(1)
    @QueryParam("page")
    @DefaultValue("1")
    private Integer page = 1;

    @NotNull
    @Min(1)
    @QueryParam("pageSize")
    @DefaultValue("10")
    private Integer pageSize = 10;
}
