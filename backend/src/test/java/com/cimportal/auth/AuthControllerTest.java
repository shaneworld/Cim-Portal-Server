package com.cimportal.auth;

import com.cimportal.setting.SecuritySetting;
import com.cimportal.setting.SecuritySettingRepository;
import com.cimportal.setting.SecuritySettingService;
import com.cimportal.support.MariaDbIntegrationTest;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AuthControllerTest extends MariaDbIntegrationTest {

    private static final String DEV_PASSWORD = "cimp@123";

    @Autowired MockMvc mvc;
    @Autowired UserInfoRepository users;
    @Autowired SecuritySettingRepository settingRepo;
    @Autowired SecuritySettingService settingService;

    @BeforeEach
    void seed() {
        users.deleteAll();
        users.save(new UserInfo("EMP1", "员工一", "Employee One", "IT", "OPERATOR",
                                null, true, Instant.now()));
        users.save(new UserInfo("INACTIVE", "停用", "Inactive", "IT", "OPERATOR",
                                null, false, Instant.now()));

        // Ensure the seeded dev password hash is in place (V6 migration seeds cimp@123).
        // Reset any mutations from other tests.
        SecuritySetting s = settingRepo.findById(1L).orElseThrow();
        s.setInternalPasswordHash("$2a$10$celdHPCndrjS1gyf6jvcAe4/soZAQL/mKHsqdX5af/OCTFihFuO.y");
        settingRepo.save(s);
        settingService.invalidateCache();
    }

    // ── success ───────────────────────────────────────────────────────────────

    @Test
    void validCredentials_returns200WithAccessToken() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"EMP1\",\"password\":\"" + DEV_PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    void loginEndpoint_reachableAnonymously() throws Exception {
        // Should reach the handler (not rejected by security) — even wrong creds give 401 from
        // the controller logic, not 401 from the security filter.
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"EMP1\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // ── failure cases ─────────────────────────────────────────────────────────

    @Test
    void wrongPassword_returns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"EMP1\",\"password\":\"wrongPassword\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void unknownEmployee_returns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"GHOST\",\"password\":\"" + DEV_PASSWORD + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void inactiveEmployee_returns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"INACTIVE\",\"password\":\"" + DEV_PASSWORD + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void blankEmployeeId_returns400() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"\",\"password\":\"" + DEV_PASSWORD + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void blankPassword_returns400() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"EMP1\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest());
    }
}
