package com.cimportal.portal;

import com.cimportal.auth.CurrentUser;
import com.cimportal.group.PermissionGroupMemberRepository;
import com.cimportal.link.LinkAccessGrantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared helper: answers "can this user access a specific link?"
 * Used by both HomeService (bulk) and FavoriteController (per-link).
 */
@Service
public class PermissionService {

    private final LinkAccessGrantRepository grants;
    private final PermissionGroupMemberRepository memberRepo;

    public PermissionService(LinkAccessGrantRepository grants,
                             PermissionGroupMemberRepository memberRepo) {
        this.grants = grants;
        this.memberRepo = memberRepo;
    }

    /** Returns true if the user has access to the given link. */
    @Transactional(readOnly = true)
    public boolean isAccessible(CurrentUser user, Long linkId) {
        var linkGrants = grants.findByLinkId(linkId);
        Set<String> groupCodes = new HashSet<>(
            memberRepo.findActiveGroupCodesByEmployeeId(user.employeeId()));
        return PermissionResolver.isVisible(
            user.departmentCode(), user.roleCode(), groupCodes, linkGrants);
    }
}
