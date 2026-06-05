package com.cimportal.auth;

public record CurrentUser(String employeeId, String departmentCode, String roleCode, boolean admin) {
    public static final String ADMIN_ROLE_CODE = "PORTAL_ADMIN";
}
