package com.cimportal.enumvalue.dto;

import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import java.time.Instant;

public record EnumValueResponse(Long id, EnumCategory category, String code,
                                String labelZh, String labelEn, int sortOrder,
                                boolean active, String color, String icon,
                                Instant createdAt, Instant updatedAt) {
    public static EnumValueResponse of(EnumValue e) {
        return new EnumValueResponse(e.getId(), e.getCategory(), e.getCode(),
            e.getLabelZh(), e.getLabelEn(), e.getSortOrder(), e.isActive(),
            e.getColor(), e.getIcon(),
            e.getCreatedAt(), e.getUpdatedAt());
    }
}
