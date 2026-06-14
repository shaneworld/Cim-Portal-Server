package com.cimportal.portal.dto;

public record HomeLink(Long id, String nameZh, String nameEn,
                       String url, String icon, String statusCode,
                       boolean openInNewTab, String environment,
                       String envColor, String envLabelZh, String envLabelEn,
                       boolean launchApp, String downloadUrl,
                       boolean accessible, boolean favorite) { }
