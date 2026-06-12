package com.cimportal.quicklink.dto;

public record QuickLinkResponse(
    Long id,
    String labelZh,
    String labelEn,
    String url,
    String icon,
    int sortOrder,
    boolean active
) { }
