package com.example.domain.requests.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateReviewRequest {

    private int userId;

    private int productId;

    @Min(value = 1, message = "Rating harus antara 1 sampai 5")
    @Max(value = 5, message = "Rating harus antara 1 sampai 5")
    private int rating;

    @NotBlank(message = "Komentar wajib diisi")
    private String comment;
}
