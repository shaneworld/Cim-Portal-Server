package com.cimportal.setting.dto;

import jakarta.validation.constraints.AssertTrue;

public record SecuritySettingUpdateRequest(
    Boolean ssoEnabled,
    String issuerUri,
    String clientId,
    String scopes,
    String usernameClaim,
    String initialPassword,
    Boolean infoPanelEnabled
) {
    @AssertTrue(message = "启用 SSO 时 issuerUri 和 clientId 不能为空")
    public boolean isSsoConfigValid() {
        if (ssoEnabled == null || !ssoEnabled) return true;
        return issuerUri != null && !issuerUri.isBlank()
            && clientId != null && !clientId.isBlank();
    }
}
