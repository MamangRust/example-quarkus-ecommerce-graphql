package com.example.domain.requests.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCartRequest {
    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    private Integer productId;

    private Integer userId;
}
