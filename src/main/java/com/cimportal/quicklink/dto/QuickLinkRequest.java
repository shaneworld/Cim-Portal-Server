package com.cimportal.quicklink.dto;

import jakarta.validation.constraints.NotBlank;

public record QuickLinkRequest(
    @NotBlank String labelZh,
    @NotBlank String labelEn,
    @NotBlank String url,
    String icon,
    Integer sortOrder,
    Boolean active
) {
    public int sortOrderOrDefault() {
        return sortOrder != null ? sortOrder : 100;
    }

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
