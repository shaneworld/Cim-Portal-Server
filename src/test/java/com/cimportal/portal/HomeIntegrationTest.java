package com.cimportal.portal;

import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.link.*;
import com.cimportal.link.LinkTestFactory;
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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class HomeIntegrationTest extends OracleIntegrationTest {
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
        EnumValue uat = new EnumValue(EnumCategory.LINK_ENV, "UAT", "测试环境", "UAT", 20, true);
        uat.setColor("amber");
        enums.save(uat);

        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));
        users.save(new UserInfo("QA1", "质量", "QA", "QA", "QA_ENGINEER", null, true, Instant.now()));
        users.save(new UserInfo("OFF", "离职", "Gone", "QA", "QA_ENGINEER", null, false, Instant.now()));

        Link open = LinkTestFactory.newLink("Public Link", "MES"); links.save(open);
        Link opOnly = LinkTestFactory.newLink("OP Only", "MES"); links.save(opOnly);
        grants.save(new LinkAccessGrant(opOnly.getId(), GrantType.ROLE, "OPERATOR"));
    }

    @Test
    void operatorSeesBothLinksAccessible() throws Exception {
        mvc.perform(get("/api/portal/home").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[0].links.length()").value(2))
            // open link: accessible, url present
            .andExpect(jsonPath("$.categories[0].links[0].accessible").value(true))
            .andExpect(jsonPath("$.categories[0].links[0].url").value("https://x"))
            // restricted link: OPERATOR role matches → accessible, url present
            .andExpect(jsonPath("$.categories[0].links[1].nameEn").value("OP Only"))
            .andExpect(jsonPath("$.categories[0].links[1].accessible").value(true))
            .andExpect(jsonPath("$.categories[0].links[1].url").value("https://x"));
    }

    @Test
    void qaSeesAllLinksButRestrictedOnesLocked() throws Exception {
        // QA1 has role QA_ENGINEER — no match for the OPERATOR-restricted "OP Only" link
        mvc.perform(get("/api/portal/home").header("Authorization", "Bearer " + jwts.bearerFor("QA1")))
            .andExpect(status().isOk())
            // ALL active links are returned — no skipping
            .andExpect(jsonPath("$.categories[0].links.length()").value(2))
            // public link: no grants → accessible=true, url exposed
            .andExpect(jsonPath("$.categories[0].links[0].nameEn").value("Public Link"))
            .andExpect(jsonPath("$.categories[0].links[0].accessible").value(true))
            .andExpect(jsonPath("$.categories[0].links[0].url").value("https://x"))
            // OP Only link: QA_ENGINEER has no match → accessible=false, url hidden
            .andExpect(jsonPath("$.categories[0].links[1].nameEn").value("OP Only"))
            .andExpect(jsonPath("$.categories[0].links[1].accessible").value(false))
            .andExpect(jsonPath("$.categories[0].links[1].url").doesNotExist());
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
        Link uatLink = LinkTestFactory.newLink("UAT Link", "MES", "UAT");
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
            // UAT link carries environment value + inlined env presentation
            .andExpect(jsonPath("$.categories[0].links[2].environment").value("UAT"))
            .andExpect(jsonPath("$.categories[0].links[2].envColor").value("amber"))
            .andExpect(jsonPath("$.categories[0].links[2].envLabelEn").value("UAT"));
    }

    @Test
    void homeLinkCarriesLaunchAppAndDownloadUrl() throws Exception {
        // Seed a launch link
        Link launchLink = LinkTestFactory.newLink("Launch App", "MES");
        launchLink.setUrl("mesclient://");
        launchLink.setLaunchApp(true);
        launchLink.setDownloadUrl("https://downloads.example.com/mes-client-setup.exe");
        links.save(launchLink);

        mvc.perform(get("/api/portal/home").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            // plain links default to launchApp=false
            .andExpect(jsonPath("$.categories[0].links[0].launchApp").value(false))
            .andExpect(jsonPath("$.categories[0].links[0].downloadUrl").doesNotExist())
            // launch link carries both fields
            .andExpect(jsonPath("$.categories[0].links[2].launchApp").value(true))
            .andExpect(jsonPath("$.categories[0].links[2].downloadUrl")
                .value("https://downloads.example.com/mes-client-setup.exe"));
    }
}
