package com.cimportal.portal;

import com.cimportal.link.GrantType;
import com.cimportal.link.LinkAccessGrant;

import java.util.List;
import java.util.Set;

/** Pure function: given a user's department/role/groups and a link's grants, decide visibility. No IO. */
public final class PermissionResolver {
    private PermissionResolver() { }

    /**
     * Full 4-arg overload: union of DEPARTMENT, ROLE, and GROUP grants.
     * Empty grants → visible to everyone (existing behaviour preserved).
     */
    public static boolean isVisible(String departmentCode, String roleCode,
                                    Set<String> userGroupCodes, List<LinkAccessGrant> grants) {
        if (grants == null || grants.isEmpty()) return true;
        for (LinkAccessGrant g : grants) {
            switch (g.getGrantType()) {
                case DEPARTMENT -> { if (g.getGrantCode().equals(departmentCode)) return true; }
                case ROLE       -> { if (g.getGrantCode().equals(roleCode)) return true; }
                case GROUP      -> { if (userGroupCodes != null && userGroupCodes.contains(g.getGrantCode())) return true; }
            }
        }
        return false;
    }

    /**
     * Legacy 3-arg overload — delegates to the 4-arg form with an empty group set.
     * All existing callers and tests continue to compile and behave identically.
     */
    public static boolean isVisible(String departmentCode, String roleCode, List<LinkAccessGrant> grants) {
        return isVisible(departmentCode, roleCode, Set.of(), grants);
    }
}
