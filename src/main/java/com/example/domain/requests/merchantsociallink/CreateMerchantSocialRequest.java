package com.example.domain.requests.merchantsociallink;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateMerchantSocialRequest {

    @NotNull(message = "ID detail merchant wajib diisi")
    private Integer merchantDetailId;

    @NotBlank(message = "Nama platform wajib diisi")
    private String platform;

    @NotBlank(message = "URL wajib diisi")
    @Pattern(regexp = "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$", message = "URL harus berupa tautan yang valid")
    private String url;
}
