package com.cimportal.auth;

public record MeResponse(String employeeId, String displayNameZh, String displayNameEn,
                         String departmentCode, String roleCode, boolean isAdmin) { }
