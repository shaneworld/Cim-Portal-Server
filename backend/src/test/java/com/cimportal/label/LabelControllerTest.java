package com.cimportal.label;

import com.cimportal.support.MariaDbIntegrationTest;
import com.cimportal.support.TestJwts;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class LabelControllerTest extends MariaDbIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired LabelRepository labels;

    @BeforeEach
    void seed() {
        users.deleteAll(); labels.deleteAll();
        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));
        labels.save(new Label("portal.title", "SYSTEM_NAME", "门户", "Portal"));
    }

    @Test
    void anyAuthedUserReadsI18nMap() throws Exception {
        mvc.perform(get("/api/i18n/labels").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$['portal.title'].zh").value("门户"))
            .andExpect(jsonPath("$['portal.title'].type").value("SYSTEM_NAME"));
    }

    @Test
    void filtersLabelsByTypeAndQuery() throws Exception {
        String adminToken = "Bearer " + jwts.bearerFor("ADMIN1");

        // Seed a second label with a different type
        labels.save(new Label("nav.dashboard", "UI_TEXT", "仪表盘", "Dashboard"));

        // Filter by type=UI_TEXT → only nav.dashboard
        mvc.perform(get("/api/admin/labels?type=UI_TEXT").header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].labelKey").value("nav.dashboard"));

        // Filter by type=SYSTEM_NAME → only portal.title
        mvc.perform(get("/api/admin/labels?type=SYSTEM_NAME").header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].labelKey").value("portal.title"));

        // Filter by q=portal (matches portal.title labelKey) → only portal.title
        mvc.perform(get("/api/admin/labels?q=portal").header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].labelKey").value("portal.title"));

        // No filters → both labels
        mvc.perform(get("/api/admin/labels").header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void onlyAdminCreates() throws Exception {
        String body = "{\"labelKey\":\"nav.admin\",\"type\":\"UI_TEXT\",\"textZh\":\"管理\",\"textEn\":\"Admin\"}";
        mvc.perform(post("/api/admin/labels").header("Authorization", "Bearer " + jwts.bearerFor("OP1"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/labels").header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }
}
