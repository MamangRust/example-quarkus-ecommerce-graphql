package com.example.domain.requests.slider;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import com.example.domain.requests.FileUpload;

@Data
public class CreateSliderRequest {

    @NotNull
    private String nama;

    @NotNull
    private FileUpload filePath;
}
