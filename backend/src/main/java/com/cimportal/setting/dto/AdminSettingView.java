package com.cimportal.setting.dto;

import java.time.Instant;

public record AdminSettingView(
    boolean ssoEnabled,
    String issuerUri,
    String clientId,
    String scopes,
    String usernameClaim,
    Instant updatedAt
) { }
