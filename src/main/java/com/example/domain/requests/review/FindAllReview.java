package com.example.domain.requests.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class FindAllReview {

    @NotBlank(message = "Kata kunci pencarian wajib diisi")
    @QueryParam("search")
    @DefaultValue("")
    private String search = "";

    @Min(value = 1, message = "Nomor halaman minimal 1")
    @QueryParam("page")
    @DefaultValue("1")
    private int page = 1;

    @Min(value = 1, message = "Ukuran halaman minimal 1")
    @Max(value = 100, message = "Ukuran halaman maksimal 100")
    @QueryParam("pageSize")
    @DefaultValue("20")
    private int pageSize = 20;
}
