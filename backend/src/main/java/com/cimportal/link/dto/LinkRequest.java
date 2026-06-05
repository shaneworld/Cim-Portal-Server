package com.cimportal.link.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkRequest(
    @NotBlank String code, @NotBlank String nameZh, @NotBlank String nameEn,
    @NotBlank String url, @NotBlank String icon,
    @NotBlank String categoryCode, @NotBlank String statusCode,
    int sortOrder, Boolean openInNewTab
) {
    public boolean openInNewTabOrDefault() { return openInNewTab == null || openInNewTab; }
}
