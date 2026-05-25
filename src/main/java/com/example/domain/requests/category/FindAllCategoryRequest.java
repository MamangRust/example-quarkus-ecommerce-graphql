package com.example.domain.requests.category;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class FindAllCategoryRequest {

    @NotBlank
    @QueryParam("search")
    @DefaultValue("")
    private String search = "";

    @NotNull
    @Min(1)
    @QueryParam("page")
    @DefaultValue("1")
    private Integer page = 1;

    @NotNull
    @Min(1)
    @Max(100)
    @QueryParam("pageSize")
    @DefaultValue("10")
    private Integer pageSize = 10;
}
