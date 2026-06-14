package com.cimportal.setting.dto;

import java.time.Instant;

public record AdminSettingView(
    boolean ssoEnabled,
    String issuerUri,
    String clientId,
    String scopes,
    String usernameClaim,
    boolean infoPanelEnabled,
    boolean heroEnabled,
    String dutyApiBaseUrl,
    boolean dutyApiKeyConfigured,
    String larkBaseUrl,
    String larkAppId,
    String larkReceiverId,
    String larkReceiverIdType,
    boolean larkAppSecretConfigured,
    Instant updatedAt
) { }
