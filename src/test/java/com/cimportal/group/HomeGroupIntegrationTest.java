package com.cimportal.group;

import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.link.*;
import com.cimportal.support.OracleIntegrationTest;
import com.cimportal.support.TestJwts;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test: a link restricted by a GROUP grant is visible only to group members.
 * Verifies the union behaviour added in Task 1.7.
 */
@AutoConfigureMockMvc
class HomeGroupIntegrationTest extends OracleIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired EnumValueRepository enums;
    @Autowired LinkRepository links;
    @Autowired LinkAccessGrantRepository grants;
    @Autowired PermissionGroupRepository groups;
    @Autowired PermissionGroupMemberRepository members;

    @BeforeEach
    void seed() {
        members.deleteAll();
        groups.deleteAll();
        grants.deleteAll();
        links.deleteAll();
        users.deleteAll();
        enums.deleteAll();

        enums.save(new EnumValue(EnumCategory.LINK_CATEGORY, "MES", "制造执行", "MES", 1, true));
        enums.save(new EnumValue(EnumCategory.LINK_STATUS, "ACTIVE", "启用", "Active", 1, true));

        // Employee A is in group G1; Employee B is not
        users.save(new UserInfo("EMP_A", "员工A", "EmpA", "IT", "OPERATOR", null, true, Instant.now()));
        users.save(new UserInfo("EMP_B", "员工B", "EmpB", "QA", "QA_ENGINEER", null, true, Instant.now()));

        // Create group G1 and add EMP_A
        PermissionGroup g1 = new PermissionGroup();
        g1.setCode("G1");
        g1.setNameZh("测试组");
        g1.setNameEn("Test Group");
        g1.setActive(true);
        groups.save(g1);
        members.save(new PermissionGroupMember(g1.getId(), "EMP_A"));

        // Create a link restricted to GROUP=G1
        Link restricted = LinkTestFactory.newLink("Group Restricted", "MES");
        links.save(restricted);
        grants.save(new LinkAccessGrant(restricted.getId(), GrantType.GROUP, "G1"));
    }

    @Test
    void groupMember_seesLink_accessible() throws Exception {
        mvc.perform(get("/api/portal/home").header("Authorization", "Bearer " + jwts.bearerFor("EMP_A")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[0].links[0].nameEn").value("Group Restricted"))
            .andExpect(jsonPath("$.categories[0].links[0].accessible").value(true))
            .andExpect(jsonPath("$.categories[0].links[0].url").value("https://x"));
    }

    @Test
    void nonGroupMember_seesLink_locked() throws Exception {
        mvc.perform(get("/api/portal/home").header("Authorization", "Bearer " + jwts.bearerFor("EMP_B")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[0].links[0].nameEn").value("Group Restricted"))
            .andExpect(jsonPath("$.categories[0].links[0].accessible").value(false))
            .andExpect(jsonPath("$.categories[0].links[0].url").doesNotExist());
    }
}
