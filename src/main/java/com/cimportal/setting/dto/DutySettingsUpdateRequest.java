package com.cimportal.setting.dto;

public record DutySettingsUpdateRequest(
    String dutyApiBaseUrl,
    String dutyApiKey
) { }
