package com.cimportal.announcement.dto;

import com.cimportal.announcement.AnnouncementType;

public record AnnouncementTypeResponse(
    Long id,
    String code,
    String labelZh,
    String labelEn,
    String color,
    String icon,
    int sortOrder,
    boolean active
) {
    public static AnnouncementTypeResponse of(AnnouncementType t) {
        return new AnnouncementTypeResponse(
            t.getId(), t.getCode(), t.getLabelZh(), t.getLabelEn(),
            t.getColor(), t.getIcon(), t.getSortOrder(), t.isActive()
        );
    }
}
