package com.cimportal.announcement.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record AnnouncementRequest(
    @NotBlank String titleZh,
    @NotBlank String titleEn,
    @NotBlank String bodyZh,
    @NotBlank String bodyEn,
    @NotBlank String typeCode,
    Boolean pinned,
    Instant startsAt,
    Instant endsAt,
    Boolean active
) {
    public boolean pinnedOrDefault() {
        return pinned != null && pinned;
    }

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
