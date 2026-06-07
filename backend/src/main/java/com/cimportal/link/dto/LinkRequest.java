package com.cimportal.link.dto;

import com.cimportal.link.LinkEnv;
import jakarta.validation.constraints.NotBlank;

public record LinkRequest(
    @NotBlank String nameZh,
    @NotBlank String nameEn,
    @NotBlank String url,
    @NotBlank String icon,
    @NotBlank String categoryCode,
    @NotBlank String statusCode,
    int sortOrder,
    Boolean openInNewTab,
    LinkEnv environment
) {
    public boolean openInNewTabOrDefault() { return openInNewTab == null || openInNewTab; }
}
