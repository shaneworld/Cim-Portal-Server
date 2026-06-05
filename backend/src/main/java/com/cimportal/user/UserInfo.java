package com.cimportal.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_info")
public class UserInfo {
    @Id @Column(name = "employee_id", length = 64) private String employeeId;
    @Column(name = "display_name_zh", nullable = false, length = 255) private String displayNameZh;
    @Column(name = "display_name_en", nullable = false, length = 255) private String displayNameEn;
    @Column(name = "department_code", nullable = false, length = 64) private String departmentCode;
    @Column(name = "role_code", nullable = false, length = 64) private String roleCode;
    @Column private String email;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "synced_at", nullable = false) private Instant syncedAt;

    protected UserInfo() { }
    public UserInfo(String employeeId, String displayNameZh, String displayNameEn,
                    String departmentCode, String roleCode, String email,
                    boolean active, Instant syncedAt) {
        this.employeeId = employeeId; this.displayNameZh = displayNameZh;
        this.displayNameEn = displayNameEn; this.departmentCode = departmentCode;
        this.roleCode = roleCode; this.email = email; this.active = active; this.syncedAt = syncedAt;
    }
    public String getEmployeeId() { return employeeId; }
    public String getDisplayNameZh() { return displayNameZh; }
    public String getDisplayNameEn() { return displayNameEn; }
    public String getDepartmentCode() { return departmentCode; }
    public String getRoleCode() { return roleCode; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
    public Instant getSyncedAt() { return syncedAt; }
}
