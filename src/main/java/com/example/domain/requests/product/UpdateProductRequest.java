package com.example.domain.requests.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.example.domain.requests.FileUpload;

@Data
public class UpdateProductRequest {

    @NotNull
    private Integer productId;

    @NotNull
    private Integer merchantId;

    @NotNull
    private Integer categoryId;

    @NotNull
    @Size(min = 1)
    private String name;

    @NotNull
    private String description;

    @NotNull
    @Min(0)
    private Integer price;

    @NotNull
    @Min(0)
    private Integer countInStock;

    @NotNull
    private String brand;

    @NotNull
    @Min(0)
    private Integer weight;

    @NotNull
    @Min(0)
    private Integer rating;

    @NotNull
    private String slugProduct;
    private FileUpload imageProduct;
}
