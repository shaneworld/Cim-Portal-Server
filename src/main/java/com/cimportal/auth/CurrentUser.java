package com.cimportal.auth;

import com.cimportal.common.AppConstants;

public record CurrentUser(String employeeId, String departmentCode, String roleCode, boolean admin) {
    public static final String ADMIN_ROLE_CODE = AppConstants.Roles.PORTAL_ADMIN;
}
