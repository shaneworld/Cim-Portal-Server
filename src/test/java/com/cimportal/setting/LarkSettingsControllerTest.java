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
class LarkSettingsControllerTest extends OracleIntegrationTest {

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

        // Reset lark columns to known defaults before each test
        SecuritySetting s = settingRepo.findById(1L).orElseThrow();
        s.setLarkBaseUrl(null);
        s.setLarkAppId(null);
        s.setLarkAppSecret(null);
        s.setLarkReceiverId(null);
        s.setLarkReceiverIdType(null);
        s.setUpdatedAt(null);
        settingRepo.save(s);

        // Invalidate in-memory cache so tests see the reset DB state
        service.invalidateCache();
    }

    @Test
    void adminGet_requiresPortalAdmin() throws Exception {
        // anonymous → 401
        mvc.perform(get("/api/admin/lark-settings"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminPut_setsFields_withoutEchoingPlaintextSecret() throws Exception {
        String req = """
            {"larkBaseUrl":"https://open.feishu.cn","larkAppId":"cli_app123",
             "larkAppSecret":"super-secret-lark","larkReceiverId":"ops@example.com",
             "larkReceiverIdType":"email"}
            """;

        String body = mvc.perform(put("/api/admin/lark-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.larkBaseUrl").value("https://open.feishu.cn"))
            .andExpect(jsonPath("$.larkAppId").value("cli_app123"))
            .andExpect(jsonPath("$.larkReceiverId").value("ops@example.com"))
            .andExpect(jsonPath("$.larkReceiverIdType").value("email"))
            .andExpect(jsonPath("$.larkAppSecretConfigured").value(true))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("super-secret-lark").doesNotContain("larkAppSecret\"");
    }

    @Test
    void adminPut_omittingSecret_keepsExistingSecret() throws Exception {
        // First set a secret
        mvc.perform(put("/api/admin/lark-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"larkAppId\":\"cli_app1\",\"larkAppSecret\":\"s1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.larkAppSecretConfigured").value(true));

        // PUT with only larkAppId (no secret) → existing secret kept, still configured
        mvc.perform(put("/api/admin/lark-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"larkAppId\":\"cli_app2\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.larkAppId").value("cli_app2"))
            .andExpect(jsonPath("$.larkAppSecretConfigured").value(true));
    }

    @Test
    void adminPut_blankSecret_clearsSecret() throws Exception {
        // First set a secret
        mvc.perform(put("/api/admin/lark-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"larkAppId\":\"cli_app1\",\"larkAppSecret\":\"s1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.larkAppSecretConfigured").value(true));

        // Blank secret → cleared
        mvc.perform(put("/api/admin/lark-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"larkAppSecret\":\"\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.larkAppSecretConfigured").value(false));
    }

    @Test
    void publicConfig_larkEnabled_reflectsConfig() throws Exception {
        // All required → larkEnabled=true
        mvc.perform(put("/api/admin/lark-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"larkAppId\":\"cli_x\",\"larkAppSecret\":\"sec\",\"larkReceiverId\":\"u@e.com\",\"larkReceiverIdType\":\"email\"}"))
            .andExpect(status().isOk());

        String body = mvc.perform(get("/api/portal/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.larkEnabled").value(true))
            .andReturn().getResponse().getContentAsString();
        assertThat(body)
            .doesNotContain("cli_x")
            .doesNotContain("sec")
            .doesNotContain("u@e.com");

        // Clear the receiver → larkEnabled=false
        mvc.perform(put("/api/admin/lark-settings")
                .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"larkReceiverId\":\"\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/portal/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.larkEnabled").value(false));
    }
}
