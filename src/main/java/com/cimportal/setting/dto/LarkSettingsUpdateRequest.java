package com.cimportal.setting.dto;

public record LarkSettingsUpdateRequest(
    String larkBaseUrl,
    String larkAppId,
    String larkAppSecret,
    String larkReceiverId,
    String larkReceiverIdType
) { }
