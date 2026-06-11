package com.cimportal.group.dto;

import com.cimportal.group.PermissionGroup;

public record GroupResponse(Long id, String code, String nameZh, String nameEn, boolean active) {
    public static GroupResponse of(PermissionGroup g) {
        return new GroupResponse(g.getId(), g.getCode(), g.getNameZh(), g.getNameEn(), g.isActive());
    }
}
