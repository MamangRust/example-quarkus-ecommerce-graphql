package com.example.domain.requests.category;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import com.example.domain.requests.FileUpload;

@Data
public class CreateCategoryRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String description;

    @NotBlank
    private String slugCategory;

    @jakarta.validation.constraints.NotNull
    private FileUpload imageCategory;
}
