package com.cimportal.setting.dto;

public record DutySettingsView(
    String dutyApiBaseUrl,
    boolean dutyApiKeyConfigured
) { }
