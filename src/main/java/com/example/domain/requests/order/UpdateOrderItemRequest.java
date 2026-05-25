package com.example.domain.requests.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateOrderItemRequest {

    @NotNull
    private Integer orderItemId;

    @NotNull
    private Integer productId;

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    private Integer price;
}
