package com.example.domain.requests.banner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;

import lombok.Data;

@Data
public class UpdateBannerRequest {
    @Null
    private Integer bannerID;

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private String startDate;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private String endDate;

    @NotBlank
    @Pattern(regexp = "\\d{2}:\\d{2}")
    private String startTime;

    @NotBlank
    @Pattern(regexp = "\\d{2}:\\d{2}")
    private String endTime;

    @NotNull
    private Boolean isActive;
}
