package com.cimportal.portal;

import com.cimportal.link.GrantType;
import com.cimportal.link.LinkAccessGrant;

import java.util.List;

/** Pure function: given a user's department/role and a link's grants, decide visibility. No IO. */
public final class PermissionResolver {
    private PermissionResolver() { }

    public static boolean isVisible(String departmentCode, String roleCode, List<LinkAccessGrant> grants) {
        if (grants == null || grants.isEmpty()) return true;   // no grants = visible to everyone
        for (LinkAccessGrant g : grants) {
            if (g.getGrantType() == GrantType.DEPARTMENT && g.getGrantCode().equals(departmentCode)) return true;
            if (g.getGrantType() == GrantType.ROLE && g.getGrantCode().equals(roleCode)) return true;
        }
        return false;
    }
}
