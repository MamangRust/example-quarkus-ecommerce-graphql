package com.example.domain.requests.reviewdetail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import com.example.domain.requests.FileUpload;

@Data
public class UpdateReviewDetailRequest {

    @NotNull(message = "ID detail review wajib diisi")
    private Integer reviewDetailId;

    @NotBlank(message = "Tipe media wajib diisi")
    private String type;

    @NotNull(message = "File wajib diunggah")
    private FileUpload file;

    @NotBlank(message = "Caption wajib diisi")
    private String caption;
}
