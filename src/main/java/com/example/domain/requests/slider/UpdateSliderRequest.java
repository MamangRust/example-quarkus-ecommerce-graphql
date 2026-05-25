package com.example.domain.requests.slider;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import com.example.domain.requests.FileUpload;

@Data
public class UpdateSliderRequest {

    @NotNull
    private Integer id;

    @NotNull
    private String nama;
    private FileUpload filePath;
}
