package com.cimportal.group;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "permission_group")
public class PermissionGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 64, unique = true) private String code;
    @Column(name = "name_zh", nullable = false, length = 255) private String nameZh;
    @Column(name = "name_en", nullable = false, length = 255) private String nameEn;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String c) { this.code = c; }
    public String getNameZh() { return nameZh; }
    public void setNameZh(String v) { this.nameZh = v; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String v) { this.nameEn = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
