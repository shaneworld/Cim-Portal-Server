package com.cimportal.dutyline.dto;

import jakarta.validation.constraints.NotBlank;

public record DutyLineRequest(
    @NotBlank String labelZh,
    @NotBlank String labelEn,
    @NotBlank String phone,
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
