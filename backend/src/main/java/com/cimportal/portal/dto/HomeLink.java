package com.cimportal.portal.dto;

import com.cimportal.link.LinkEnv;

public record HomeLink(Long id, String nameZh, String nameEn,
                       String url, String icon, String statusCode,
                       boolean openInNewTab, LinkEnv environment) { }
