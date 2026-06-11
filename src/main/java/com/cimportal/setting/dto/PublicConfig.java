package com.cimportal.setting.dto;

public record PublicConfig(
    boolean ssoEnabled,
    String authority,
    String clientId,
    String scopes,
    String usernameClaim,
    boolean infoPanelEnabled
) { }
