package com.cimportal.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnnouncementTypeRequest(
    @NotBlank String code,
    @NotBlank String labelZh,
    @NotBlank String labelEn,
    @NotBlank String color,
    @NotBlank String icon,
    @NotNull  Integer sortOrder,
    Boolean active
) {
    public boolean activeOrDefault() {
        return active == null || active;
    }
}
