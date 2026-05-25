package com.example.domain.requests.merchantdetail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import com.example.domain.requests.FileUpload;

@Data
public class UpdateMerchantDetailRequest {

    @NotNull(message = "ID merchant detail wajib diisi")
    private Integer merchantDetailId;

    @NotBlank(message = "Nama merchant wajib diisi")
    private String displayName;

    @NotNull(message = "Cover image wajib diunggah")
    private FileUpload coverImageUrl;

    @NotNull(message = "Logo wajib diunggah")
    private FileUpload logoUrl;

    @NotBlank(message = "Deskripsi singkat wajib diisi")
    private String shortDescription;

    @Pattern(regexp = "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$", message = "Website harus berupa URL yang valid")
    private String websiteUrl;
}
