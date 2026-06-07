package com.cimportal.portal.dto;

public record HomeLink(Long id, String nameZh, String nameEn,
                       String url, String urlDev, String urlUat, String urlRelease,
                       String icon, String statusCode, boolean openInNewTab) { }
