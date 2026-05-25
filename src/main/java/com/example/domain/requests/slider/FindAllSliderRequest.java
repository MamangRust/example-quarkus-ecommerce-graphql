package com.example.domain.requests.slider;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class FindAllSliderRequest {

    @NotNull
    @QueryParam("search")
    @DefaultValue("")
    private String search = "";

    @Min(1)
    @QueryParam("page")
    @DefaultValue("1")
    private int page = 1;

    @Min(1)
    @QueryParam("pageSize")
    @DefaultValue("10")
    private int pageSize = 10;
}
