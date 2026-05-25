package com.example.domain.requests.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class FindAllReviewByProduct {

    @QueryParam("productId")
    private Integer productId;

    @Min(value = 1, message = "Rating harus antara 1 sampai 5")
    @Max(value = 5, message = "Rating harus antara 1 sampai 5")
    @QueryParam("rating")
    private Integer rating;

    @NotBlank(message = "Kata kunci pencarian wajib diisi")
    @QueryParam("search")
    @DefaultValue("")
    private String search = "";

    @Min(value = 1, message = "Nomor halaman minimal 1")
    @QueryParam("page")
    @DefaultValue("1")
    private Integer page = 1;

    @Min(value = 1, message = "Ukuran halaman minimal 1")
    @Max(value = 100, message = "Ukuran halaman maksimal 100")
    @QueryParam("pageSize")
    @DefaultValue("20")
    private Integer pageSize = 20;
}
