package com.example.domain.response.banner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import com.example.entity.Banner;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BannerResponseDeleteAt {
    private Long id;
    private String name;
    private String startDate;
    private String endDate;
    private String startTime;
    private String endTime;
    private Boolean isActive;
    private String createdAt;
    private String updatedAt;
    private String deletedAt;

    public static BannerResponseDeleteAt from(Banner banner) {
        return BannerResponseDeleteAt.builder()
                .id(banner.id)
                .name(banner.getName())
                .startDate(banner.getStartDate() != null ? banner.getStartDate().toString() : null)
                .endDate(banner.getEndDate() != null ? banner.getEndDate().toString() : null)
                .startTime(banner.getStartTime() != null ? banner.getStartTime().toString() : null)
                .endTime(banner.getEndTime() != null ? banner.getEndTime().toString() : null)
                .isActive(banner.getIsActive())
                .createdAt(banner.getCreatedAt() != null ? banner.getCreatedAt().toString() : null)
                .updatedAt(banner.getUpdatedAt() != null ? banner.getUpdatedAt().toString() : null)
                .deletedAt(banner.getDeletedAt() != null ? banner.getDeletedAt().toString() : null)
                .build();
    }
}