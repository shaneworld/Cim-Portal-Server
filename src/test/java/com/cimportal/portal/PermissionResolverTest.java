package com.cimportal.portal;

import com.cimportal.link.GrantType;
import com.cimportal.link.LinkAccessGrant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionResolverTest {

    private LinkAccessGrant grant(GrantType t, String code) {
        return new LinkAccessGrant(1L, t, code);
    }

    @Test
    void emptyGrantsVisibleToEveryone() {
        assertThat(PermissionResolver.isVisible("ANY", "ANY", List.of())).isTrue();
    }

    @Test
    void visibleWhenDepartmentMatches() {
        var grants = List.of(grant(GrantType.DEPARTMENT, "FAB1-PROD"));
        assertThat(PermissionResolver.isVisible("FAB1-PROD", "OPERATOR", grants)).isTrue();
    }

    @Test
    void visibleWhenRoleMatches() {
        var grants = List.of(grant(GrantType.ROLE, "PROCESS_ENGINEER"));
        assertThat(PermissionResolver.isVisible("QA", "PROCESS_ENGINEER", grants)).isTrue();
    }

    @Test
    void hiddenWhenNeitherMatches() {
        var grants = List.of(grant(GrantType.DEPARTMENT, "IT"), grant(GrantType.ROLE, "ADMIN"));
        assertThat(PermissionResolver.isVisible("QA", "OPERATOR", grants)).isFalse();
    }
}
