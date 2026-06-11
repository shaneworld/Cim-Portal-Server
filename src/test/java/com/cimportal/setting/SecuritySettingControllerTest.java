package com.cimportal.setting;

import com.cimportal.support.OracleIntegrationTest;
import com.cimportal.support.TestJwts;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class SecuritySettingControllerTest extends OracleIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired SecuritySettingService service;
    @Autowired SecuritySettingRepository settingRepo;

    @BeforeEach
    void seed() {
        users.deleteAll();
        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));

        // Reset security_setting to known defaults before each test
        SecuritySetting s = settingRepo.findById(1L).orElseThrow();
        s.setSsoEnabled(false);
        s.setSsoIssuerUri(null);
        s.setSsoClientId(null);
        s.setSsoScopes("openid profile");
        s.setSsoUsernameClaim("preferred_username");
        s.setInfoPanelEnabled(true);
        s.setUpdatedAt(null);
        settingRepo.save(s);

        // Invalidate in-memory cache so tests see the reset DB state
        service.invalidateCache();
    }

    // ── Public config ─────────────────────────────────────────────────────────

    @Test
    void publicConfig_reachableAnonymously() throws Exception {
        mvc.perform(get("/api/portal/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ssoEnabled").value(false))
            .andExpect(jsonPath("$.scopes").value("openid profile"))
            .andExpect(jsonPath("$.usernameClaim").value("preferred_username"));
    }

    @Test
    void publicConfig_doesNotExposeHash() throws Exception {
        String body = mvc.perform(get("/api/portal/config"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("hash").doesNotContain("password");
    }

    // ── Admin GET ─────────────────────────────────────────────────────────────

    @Test
    void adminGet_requiresPortalAdmin() throws Exception {
        // anonymous → 401
        mvc.perform(get("/api/admin/security-settings"))
            .andExpect(status().isUnauthorized());

        // non-admin → 403
        mvc.perform(get("/api/admin/security-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminGet_returnsSettingWithoutHash() throws Exception {
        String body = mvc.perform(get("/api/admin/security-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ssoEnabled").value(false))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("hash").doesNotContain("password");
    }

    // ── Admin PUT ─────────────────────────────────────────────────────────────

    @Test
    void adminPut_updatesFields_doesNotEchoHash() throws Exception {
        String req = """
            {"ssoEnabled":false,"issuerUri":"https://sso.example.com",
             "clientId":"portal","scopes":"openid","usernameClaim":"sub",
             "initialPassword":"newP@ssw0rd"}
            """;

        String body = mvc.perform(put("/api/admin/security-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issuerUri").value("https://sso.example.com"))
            .andExpect(jsonPath("$.clientId").value("portal"))
            .andExpect(jsonPath("$.scopes").value("openid"))
            .andExpect(jsonPath("$.usernameClaim").value("sub"))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("hash").doesNotContain("password");
    }

    @Test
    void adminPut_passwordUpdate_matchesInternalPassword() throws Exception {
        String req = """
            {"initialPassword":"s3cretPa$$"}
            """;

        mvc.perform(put("/api/admin/security-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isOk());

        assertThat(service.matchesInternalPassword("s3cretPa$$")).isTrue();
        assertThat(service.matchesInternalPassword("wrong")).isFalse();
    }

    @Test
    void adminPut_ssoEnabledWithoutIssuer_returns400() throws Exception {
        String req = """
            {"ssoEnabled":true,"issuerUri":"","clientId":""}
            """;

        mvc.perform(put("/api/admin/security-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void adminPut_ssoEnabledWithValidIssuer_succeeds() throws Exception {
        String req = """
            {"ssoEnabled":true,"issuerUri":"https://keycloak.example.com/realms/cim",
             "clientId":"cim-portal","scopes":"openid profile","usernameClaim":"preferred_username"}
            """;

        mvc.perform(put("/api/admin/security-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ssoEnabled").value(true))
            .andExpect(jsonPath("$.issuerUri").value("https://keycloak.example.com/realms/cim"));
    }
}
