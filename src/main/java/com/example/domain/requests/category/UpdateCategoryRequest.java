package com.example.domain.requests.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import lombok.Data;

import com.example.domain.requests.FileUpload;

@Data
public class UpdateCategoryRequest {

    @Null
    private Integer categoryId;

    @NotBlank
    private String name;

    @NotBlank
    private String description;

    @NotBlank
    private String slugCategory;
    private FileUpload imageCategory;
}
