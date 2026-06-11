package com.cimportal.group;

import java.io.Serializable;
import java.util.Objects;

public class PermissionGroupMemberId implements Serializable {
    private Long groupId;
    private String employeeId;

    public PermissionGroupMemberId() { }

    public PermissionGroupMemberId(Long groupId, String employeeId) {
        this.groupId = groupId;
        this.employeeId = employeeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PermissionGroupMemberId t)) return false;
        return Objects.equals(groupId, t.groupId) && Objects.equals(employeeId, t.employeeId);
    }

    @Override
    public int hashCode() { return Objects.hash(groupId, employeeId); }
}
