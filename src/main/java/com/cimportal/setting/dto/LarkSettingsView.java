package com.cimportal.setting.dto;

public record LarkSettingsView(
    String larkBaseUrl,
    String larkAppId,
    String larkReceiverId,
    String larkReceiverIdType,
    boolean larkAppSecretConfigured
) { }
