package com.cimportal.link.dto;

import jakarta.validation.constraints.AssertTrue;
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
    String environment,
    Boolean launchApp,
    String downloadUrl
) {
    public boolean openInNewTabOrDefault() { return openInNewTab == null || openInNewTab; }

    public boolean launchAppOrDefault() { return Boolean.TRUE.equals(launchApp); }

    @AssertTrue(message = "Download URL is required when launching a local app")
    public boolean isDownloadUrlPresentWhenLaunch() {
        return !launchAppOrDefault() || (downloadUrl != null && !downloadUrl.isBlank());
    }
}
