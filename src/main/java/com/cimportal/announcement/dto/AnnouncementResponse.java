package com.cimportal.announcement.dto;

import java.time.Instant;

public record AnnouncementResponse(
    Long id,
    String titleZh,
    String titleEn,
    String bodyZh,
    String bodyEn,
    String typeCode,
    String typeLabelZh,
    String typeLabelEn,
    String typeColor,
    String typeIcon,
    boolean pinned,
    Instant startsAt,
    Instant endsAt,
    boolean active,
    Instant createdAt
) { }
