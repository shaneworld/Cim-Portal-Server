package com.cimportal.portal;

import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.link.*;
import com.cimportal.link.LinkTestFactory;
import com.cimportal.support.MariaDbIntegrationTest;
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

@AutoConfigureMockMvc
class HomeIntegrationTest extends MariaDbIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired EnumValueRepository enums;
    @Autowired LinkRepository links;
    @Autowired LinkAccessGrantRepository grants;

    @BeforeEach
    void seed() {
        users.deleteAll(); enums.deleteAll(); links.deleteAll(); grants.deleteAll();
        enums.save(new EnumValue(EnumCategory.LINK_CATEGORY, "MES", "制造执行", "MES", 1, true));
        enums.save(new EnumValue(EnumCategory.LINK_STATUS, "ACTIVE", "启用", "Active", 1, true));

        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));
        users.save(new UserInfo("QA1", "质量", "QA", "QA", "QA_ENGINEER", null, true, Instant.now()));
        users.save(new UserInfo("OFF", "离职", "Gone", "QA", "QA_ENGINEER", null, false, Instant.now()));

        Link open = LinkTestFactory.newLink("Public Link", "MES"); links.save(open);
        Link opOnly = LinkTestFactory.newLink("OP Only", "MES"); links.save(opOnly);
        grants.save(new LinkAccessGrant(opOnly.getId(), GrantType.ROLE, "OPERATOR"));
    }

    @Test
    void operatorSeesBothLinks() throws Exception {
        mvc.perform(get("/api/portal/home").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[0].links.length()").value(2));
    }

    @Test
    void qaSeesOnlyPublicLink() throws Exception {
        mvc.perform(get("/api/portal/home").header("Authorization", "Bearer " + jwts.bearerFor("QA1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[0].links.length()").value(1))
            .andExpect(jsonPath("$.categories[0].links[0].nameEn").value("Public Link"));
    }

    @Test
    void inactiveUserForbidden() throws Exception {
        mvc.perform(get("/api/portal/home").header("Authorization", "Bearer " + jwts.bearerFor("OFF")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("USER_INACTIVE"));
    }

    @Test
    void unprovisionedUserNotFoundOnMe() throws Exception {
        mvc.perform(get("/api/portal/me").header("Authorization", "Bearer " + jwts.bearerFor("GHOST")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_PROVISIONED"));
    }

    @Test
    void homeLinkHasEnvironmentFieldAndNoEnvUrlFields() throws Exception {
        // Seed a UAT-environment link
        Link uatLink = LinkTestFactory.newLink("UAT Link", "MES", LinkEnv.UAT);
        links.save(uatLink);

        mvc.perform(get("/api/portal/home").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            // url field present
            .andExpect(jsonPath("$.categories[0].links[0].url").value("https://x"))
            // environment field present (null for plain link → not serialized)
            .andExpect(jsonPath("$.categories[0].links[0].code").doesNotExist())
            // no legacy env-url fields
            .andExpect(jsonPath("$.categories[0].links[0].urlDev").doesNotExist())
            .andExpect(jsonPath("$.categories[0].links[0].urlUat").doesNotExist())
            .andExpect(jsonPath("$.categories[0].links[0].urlRelease").doesNotExist())
            // UAT link carries environment value
            .andExpect(jsonPath("$.categories[0].links[2].environment").value("UAT"));
    }
}
