package com.cimportal.dutyline;

import com.cimportal.setting.SecuritySetting;
import com.cimportal.setting.SecuritySettingRepository;
import com.cimportal.setting.SecuritySettingService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class DutyLineControllerTest extends OracleIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired DutyLineRepository dutyLineRepo;
    @Autowired SecuritySettingRepository settingRepo;
    @Autowired SecuritySettingService settingService;

    String admin;
    String operator;

    @BeforeEach
    void seed() {
        dutyLineRepo.deleteAll();
        users.deleteAll();

        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));

        admin = "Bearer " + jwts.bearerFor("ADMIN1");
        operator = "Bearer " + jwts.bearerFor("OP1");

        // Reset duty_lines_enabled to true
        SecuritySetting s = settingRepo.findById(1L).orElseThrow();
        s.setDutyLinesEnabled(true);
        settingRepo.save(s);
        settingService.invalidateCache();
    }

    // ── Admin CRUD ────────────────────────────────────────────────────────────

    @Test
    void adminCreate_returns201() throws Exception {
        String body = """
            {"labelZh":"IT支持","labelEn":"IT Support","phone":"12345","sortOrder":10,"active":true}
            """;
        mvc.perform(post("/api/admin/duty-lines").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.labelZh").value("IT支持"))
            .andExpect(jsonPath("$.labelEn").value("IT Support"))
            .andExpect(jsonPath("$.phone").value("12345"))
            .andExpect(jsonPath("$.sortOrder").value(10))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void adminCreate_missingFields_returns400() throws Exception {
        String body = """
            {"labelZh":"IT支持"}
            """;
        mvc.perform(post("/api/admin/duty-lines").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void adminList_returnsAllInSortOrder() throws Exception {
        dutyLineRepo.save(new DutyLine("B部门", "Dept B", "22222", 20, true));
        dutyLineRepo.save(new DutyLine("A部门", "Dept A", "11111", 10, true));
        dutyLineRepo.save(new DutyLine("C部门", "Dept C", "33333", 30, false));

        mvc.perform(get("/api/admin/duty-lines").header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].labelEn").value("Dept A"))
            .andExpect(jsonPath("$[1].labelEn").value("Dept B"))
            .andExpect(jsonPath("$[2].labelEn").value("Dept C"));
    }

    @Test
    void adminGetById_returnsEntry() throws Exception {
        DutyLine line = dutyLineRepo.save(new DutyLine("安全部", "Security", "99999", 5, true));

        mvc.perform(get("/api/admin/duty-lines/" + line.getId()).header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").value("99999"));
    }

    @Test
    void adminGetById_notFound_returns404() throws Exception {
        mvc.perform(get("/api/admin/duty-lines/99999").header("Authorization", admin))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminUpdate_changesFields() throws Exception {
        DutyLine line = dutyLineRepo.save(new DutyLine("旧名", "Old", "00000", 50, true));

        String updated = """
            {"labelZh":"新名","labelEn":"New","phone":"88888","sortOrder":5,"active":false}
            """;
        mvc.perform(put("/api/admin/duty-lines/" + line.getId()).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(updated))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.labelZh").value("新名"))
            .andExpect(jsonPath("$.phone").value("88888"))
            .andExpect(jsonPath("$.sortOrder").value(5))
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void adminDelete_removes() throws Exception {
        DutyLine line = dutyLineRepo.save(new DutyLine("删除", "Del", "77777", 100, true));

        mvc.perform(delete("/api/admin/duty-lines/" + line.getId()).header("Authorization", admin))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/duty-lines/" + line.getId()).header("Authorization", admin))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminEndpoints_requirePortalAdmin() throws Exception {
        mvc.perform(get("/api/admin/duty-lines").header("Authorization", operator))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/duty-lines").header("Authorization", operator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"labelZh\":\"X\",\"labelEn\":\"X\",\"phone\":\"000\"}"))
            .andExpect(status().isForbidden());
    }

    // ── Portal: active ordered list ───────────────────────────────────────────

    @Test
    void portalDutyLines_returnsOnlyActiveInSortOrder() throws Exception {
        dutyLineRepo.save(new DutyLine("B运营", "Ops B", "22222", 20, true));
        dutyLineRepo.save(new DutyLine("A运营", "Ops A", "11111", 10, true));
        dutyLineRepo.save(new DutyLine("停用", "Inactive", "00000", 5, false));

        mvc.perform(get("/api/portal/duty-lines").header("Authorization", operator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].labelEn").value("Ops A"))
            .andExpect(jsonPath("$[1].labelEn").value("Ops B"));
    }

    @Test
    void portalDutyLines_excludesInactive() throws Exception {
        dutyLineRepo.save(new DutyLine("停用", "Inactive", "00000", 1, false));

        mvc.perform(get("/api/portal/duty-lines").header("Authorization", operator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void portalDutyLines_requiresAuth() throws Exception {
        mvc.perform(get("/api/portal/duty-lines"))
            .andExpect(status().isUnauthorized());
    }

    // ── Config flag ───────────────────────────────────────────────────────────

    @Test
    void publicConfig_includesDutyLinesEnabled() throws Exception {
        mvc.perform(get("/api/portal/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dutyLinesEnabled").value(true));
    }

    @Test
    void adminPut_canChangeDutyLinesEnabled() throws Exception {
        String req = """
            {"dutyLinesEnabled":false}
            """;
        mvc.perform(put("/api/admin/security-settings")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dutyLinesEnabled").value(false));

        settingService.invalidateCache();
        mvc.perform(get("/api/portal/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dutyLinesEnabled").value(false));
    }
}
