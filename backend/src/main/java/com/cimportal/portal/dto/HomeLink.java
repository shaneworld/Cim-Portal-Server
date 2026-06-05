package com.cimportal.portal.dto;

public record HomeLink(Long id, String code, String nameZh, String nameEn,
                       String url, String icon, String statusCode, boolean openInNewTab) { }
