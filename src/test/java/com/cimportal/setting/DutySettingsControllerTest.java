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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class DutySettingsControllerTest extends OracleIntegrationTest {

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

        // Reset duty columns to known defaults before each test
        SecuritySetting s = settingRepo.findById(1L).orElseThrow();
        s.setDutyApiBaseUrl(null);
        s.setDutyApiKey(null);
        s.setUpdatedAt(null);
        settingRepo.save(s);

        // Invalidate in-memory cache so tests see the reset DB state
        service.invalidateCache();
    }

    @Test
    void adminGet_requiresPortalAdmin() throws Exception {
        // anonymous → 401
        mvc.perform(get("/api/admin/duty-settings"))
            .andExpect(status().isUnauthorized());

        // non-admin → 403
        mvc.perform(get("/api/admin/duty-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminPut_setsBaseUrlAndKey_withoutEchoingPlaintextKey() throws Exception {
        String req = """
            {"dutyApiBaseUrl":"https://duty.example.com","dutyApiKey":"super-secret-key"}
            """;

        String body = mvc.perform(put("/api/admin/duty-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dutyApiBaseUrl").value("https://duty.example.com"))
            .andExpect(jsonPath("$.dutyApiKeyConfigured").value(true))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("super-secret-key").doesNotContain("dutyApiKey\"");
    }

    @Test
    void adminPut_omittingKey_keepsExistingKey() throws Exception {
        // First set a key
        mvc.perform(put("/api/admin/duty-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dutyApiBaseUrl\":\"https://duty.example.com\",\"dutyApiKey\":\"k1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dutyApiKeyConfigured").value(true));

        // PUT with only baseUrl (no key) → existing key kept, still configured
        mvc.perform(put("/api/admin/duty-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dutyApiBaseUrl\":\"https://duty2.example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dutyApiBaseUrl").value("https://duty2.example.com"))
            .andExpect(jsonPath("$.dutyApiKeyConfigured").value(true));
    }

    @Test
    void adminPut_blankKey_clearsKey() throws Exception {
        // First set a key
        mvc.perform(put("/api/admin/duty-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dutyApiBaseUrl\":\"https://duty.example.com\",\"dutyApiKey\":\"k1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dutyApiKeyConfigured").value(true));

        // Blank key → cleared
        mvc.perform(put("/api/admin/duty-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dutyApiKey\":\"\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dutyApiKeyConfigured").value(false));
    }

    @Test
    void publicConfig_doesNotExposeDutyApiFields() throws Exception {
        mvc.perform(put("/api/admin/duty-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dutyApiBaseUrl\":\"https://duty.example.com\",\"dutyApiKey\":\"k1\"}"))
            .andExpect(status().isOk());

        String body = mvc.perform(get("/api/portal/config"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(body)
            .doesNotContain("dutyApi")
            .doesNotContain("duty.example.com")
            .doesNotContain("k1");
    }
}
