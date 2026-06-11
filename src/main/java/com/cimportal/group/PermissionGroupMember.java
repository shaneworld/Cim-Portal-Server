package com.cimportal.group;

import jakarta.persistence.*;

@Entity
@Table(name = "permission_group_member")
@IdClass(PermissionGroupMemberId.class)
public class PermissionGroupMember {
    @Id @Column(name = "group_id", nullable = false) private Long groupId;
    @Id @Column(name = "employee_id", nullable = false, length = 64) private String employeeId;

    public PermissionGroupMember() { }

    public PermissionGroupMember(Long groupId, String employeeId) {
        this.groupId = groupId;
        this.employeeId = employeeId;
    }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long v) { this.groupId = v; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String v) { this.employeeId = v; }
}
