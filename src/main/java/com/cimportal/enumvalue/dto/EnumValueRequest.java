package com.cimportal.enumvalue.dto;

import jakarta.validation.constraints.NotBlank;

public record EnumValueRequest(
    @NotBlank String code,
    @NotBlank String labelZh,
    @NotBlank String labelEn,
    int sortOrder,
    Boolean active,
    String color,
    String icon
) {
    public boolean activeOrDefault() { return active == null || active; }
}
