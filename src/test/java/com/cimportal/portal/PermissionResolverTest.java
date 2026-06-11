package com.cimportal.portal;

import com.cimportal.link.GrantType;
import com.cimportal.link.LinkAccessGrant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionResolverTest {

    private LinkAccessGrant grant(GrantType t, String code) {
        return new LinkAccessGrant(1L, t, code);
    }

    // --- existing 3-arg tests (must remain unchanged) ---

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

    // --- new GROUP tests (4-arg overload) ---

    @Test
    void noGrants_visibleToAll_withGroupCodes() {
        assertThat(PermissionResolver.isVisible("IT", "OPERATOR", Set.of(), List.of())).isTrue();
    }

    @Test
    void groupGrant_memberVisible() {
        var grants = List.of(grant(GrantType.GROUP, "G1"));
        assertThat(PermissionResolver.isVisible("X", "Y", Set.of("G1"), grants)).isTrue();
    }

    @Test
    void groupGrant_nonMemberHidden() {
        var grants = List.of(grant(GrantType.GROUP, "G1"));
        assertThat(PermissionResolver.isVisible("X", "Y", Set.of("G2"), grants)).isFalse();
    }

    @Test
    void groupGrant_emptyGroupSetHidden() {
        var grants = List.of(grant(GrantType.GROUP, "G1"));
        assertThat(PermissionResolver.isVisible("X", "Y", Set.of(), grants)).isFalse();
    }

    @Test
    void deptStillWorks_withGroupOverload() {
        var grants = List.of(grant(GrantType.DEPARTMENT, "IT"));
        assertThat(PermissionResolver.isVisible("IT", "Y", Set.of(), grants)).isTrue();
    }

    @Test
    void groupAndDeptUnion_memberSeesViaGroup() {
        // grant is DEPT=IT, user dept=QA but in group G1 which has GROUP grant
        var grants = List.of(grant(GrantType.DEPARTMENT, "IT"), grant(GrantType.GROUP, "G1"));
        assertThat(PermissionResolver.isVisible("QA", "Y", Set.of("G1"), grants)).isTrue();
    }
}
