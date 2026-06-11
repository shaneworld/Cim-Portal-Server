package com.cimportal.dutyline.dto;

public record DutyLineResponse(
    Long id,
    String labelZh,
    String labelEn,
    String phone,
    int sortOrder,
    boolean active
) { }
