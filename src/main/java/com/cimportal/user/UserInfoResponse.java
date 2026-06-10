package com.cimportal.user;

import java.time.Instant;

public record UserInfoResponse(String employeeId, String displayNameZh, String displayNameEn,
                               String departmentCode, String roleCode, String email,
                               boolean active, Instant syncedAt) {
    public static UserInfoResponse of(UserInfo u) {
        return new UserInfoResponse(u.getEmployeeId(), u.getDisplayNameZh(), u.getDisplayNameEn(),
            u.getDepartmentCode(), u.getRoleCode(), u.getEmail(), u.isActive(), u.getSyncedAt());
    }
}
