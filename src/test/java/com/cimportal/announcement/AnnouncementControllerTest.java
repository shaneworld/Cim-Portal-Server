package com.cimportal.announcement;

import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AnnouncementControllerTest extends OracleIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired EnumValueRepository enumRepo;
    @Autowired AnnouncementRepository annoRepo;
    @Autowired SecuritySettingRepository settingRepo;
    @Autowired SecuritySettingService settingService;

    String admin;

    /** Seed data for the four standard ANNOUNCEMENT_TYPE enum values as defined by V12. */
    private static final Map<String, String[]> SEED_TYPES = Map.of(
        "INFO",        new String[]{"通知",    "Info",            "blue",   "info",           "10"},
        "MAINTENANCE", new String[]{"维护",    "Maintenance",     "amber",  "wrench",         "20"},
        "OUTAGE",      new String[]{"停机",    "Outage",          "red",    "triangle-alert", "30"},
        "GENERAL",     new String[]{"一般通知", "General Notice",  "slate",  "megaphone",      "40"}
    );

    @BeforeEach
    void seed() {
        annoRepo.deleteAll();

        // Ensure all four standard ANNOUNCEMENT_TYPE enum values are present
        // (another test's deleteAll may have wiped them)
        for (Map.Entry<String, String[]> entry : SEED_TYPES.entrySet()) {
            String code = entry.getKey();
            String[] v = entry.getValue();
            if (enumRepo.findByCategoryAndCode(EnumCategory.ANNOUNCEMENT_TYPE, code).isEmpty()) {
                EnumValue ev = new EnumValue(EnumCategory.ANNOUNCEMENT_TYPE, code,
                    v[0], v[1], Integer.parseInt(v[4]), true);
                ev.setColor(v[2]);
                ev.setIcon(v[3]);
                enumRepo.save(ev);
            }
        }

        users.deleteAll();
        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));

        admin = "Bearer " + jwts.bearerFor("ADMIN1");

        // Reset infoPanelEnabled to true
        SecuritySetting s = settingRepo.findById(1L).orElseThrow();
        s.setInfoPanelEnabled(true);
        settingRepo.save(s);
        settingService.invalidateCache();
    }

    // ── Announcement CRUD ─────────────────────────────────────────────────────

    private String annoBody(String typeCode) {
        return """
            {"titleZh":"标题","titleEn":"Title","bodyZh":"正文","bodyEn":"Body",
             "typeCode":"%s","pinned":false,"active":true}
            """.formatted(typeCode);
    }

    @Test
    void createAnnouncement_returns201() throws Exception {
        mvc.perform(post("/api/admin/announcements").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(annoBody("INFO")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.titleZh").value("标题"))
            .andExpect(jsonPath("$.typeCode").value("INFO"))
            .andExpect(jsonPath("$.typeColor").value("blue"))
            .andExpect(jsonPath("$.typeIcon").value("info"));
    }

    @Test
    void createAnnouncement_invalidTypeCode_returns400() throws Exception {
        mvc.perform(post("/api/admin/announcements").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(annoBody("BOGUS")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createAnnouncement_inactiveTypeCode_returns400() throws Exception {
        // Save an inactive ANNOUNCEMENT_TYPE enum value
        EnumValue inactive = new EnumValue(EnumCategory.ANNOUNCEMENT_TYPE, "INACTIVE_T", "停用", "Inactive", 99, false);
        inactive.setColor("slate");
        inactive.setIcon("info");
        enumRepo.save(inactive);

        mvc.perform(post("/api/admin/announcements").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(annoBody("INACTIVE_T")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateAnnouncement_changesFields() throws Exception {
        mvc.perform(post("/api/admin/announcements").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(annoBody("INFO")))
            .andExpect(status().isCreated());

        long id = annoRepo.findAllByOrderByPinnedDescCreatedAtDesc().get(0).getId();
        String updated = """
            {"titleZh":"更新标题","titleEn":"Updated","bodyZh":"正文","bodyEn":"Body",
             "typeCode":"MAINTENANCE","pinned":false,"active":true}
            """;
        mvc.perform(put("/api/admin/announcements/" + id).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(updated))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titleZh").value("更新标题"))
            .andExpect(jsonPath("$.typeCode").value("MAINTENANCE"))
            .andExpect(jsonPath("$.typeColor").value("amber"));
    }

    @Test
    void deleteAnnouncement_removes() throws Exception {
        mvc.perform(post("/api/admin/announcements").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(annoBody("INFO")))
            .andExpect(status().isCreated());

        long id = annoRepo.findAllByOrderByPinnedDescCreatedAtDesc().get(0).getId();
        mvc.perform(delete("/api/admin/announcements/" + id).header("Authorization", admin))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/announcements/" + id).header("Authorization", admin))
            .andExpect(status().isNotFound());
    }

    // ── Effective filter ──────────────────────────────────────────────────────

    @Test
    void portalEffective_excludesInactive() throws Exception {
        annoRepo.save(new Announcement("标题", "Title", "正文", "Body", "INFO", false, null, null, false));

        mvc.perform(get("/api/portal/announcements").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void portalEffective_excludesFutureStartsAt() throws Exception {
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        annoRepo.save(new Announcement("标题", "Title", "正文", "Body", "INFO", false, future, null, true));

        mvc.perform(get("/api/portal/announcements").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void portalEffective_excludesPastEndsAt() throws Exception {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        annoRepo.save(new Announcement("标题", "Title", "正文", "Body", "INFO", false, null, past, true));

        mvc.perform(get("/api/portal/announcements").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void portalEffective_pinnedFirst() throws Exception {
        annoRepo.save(new Announcement("普通", "Normal", "正文", "Body", "INFO", false, null, null, true));
        annoRepo.save(new Announcement("置顶", "Pinned", "正文", "Body", "INFO", true, null, null, true));

        mvc.perform(get("/api/portal/announcements").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].titleEn").value("Pinned"))
            .andExpect(jsonPath("$[0].pinned").value(true));
    }

    @Test
    void portalEffective_carriesTypeColorAndIconFromEnum() throws Exception {
        // OUTAGE is seeded: color=red, icon=triangle-alert
        annoRepo.save(new Announcement("停机", "Outage", "正文", "Body", "OUTAGE", false, null, null, true));

        mvc.perform(get("/api/portal/announcements").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].typeColor").value("red"))
            .andExpect(jsonPath("$[0].typeIcon").value("triangle-alert"))
            .andExpect(jsonPath("$[0].typeLabelZh").value("停机"))
            .andExpect(jsonPath("$[0].typeLabelEn").value("Outage"));
    }

    @Test
    void portalEffective_generalTypeSeededByV12() throws Exception {
        // GENERAL seeded: color=slate, icon=megaphone
        annoRepo.save(new Announcement("通知", "Notice", "正文", "Body", "GENERAL", false, null, null, true));

        mvc.perform(get("/api/portal/announcements").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].typeColor").value("slate"))
            .andExpect(jsonPath("$[0].typeIcon").value("megaphone"))
            .andExpect(jsonPath("$[0].typeLabelZh").value("一般通知"))
            .andExpect(jsonPath("$[0].typeLabelEn").value("General Notice"));
    }

    // ── Config flag ───────────────────────────────────────────────────────────

    @Test
    void publicConfig_includesInfoPanelEnabled() throws Exception {
        mvc.perform(get("/api/portal/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.infoPanelEnabled").value(true));
    }

    @Test
    void adminPut_canChangeInfoPanelEnabled() throws Exception {
        String req = """
            {"infoPanelEnabled":false}
            """;
        mvc.perform(put("/api/admin/security-settings")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.infoPanelEnabled").value(false));

        settingService.invalidateCache();
        mvc.perform(get("/api/portal/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.infoPanelEnabled").value(false));
    }

    // ── Auth checks ───────────────────────────────────────────────────────────

    @Test
    void adminEndpoints_requirePortalAdmin() throws Exception {
        String op = "Bearer " + jwts.bearerFor("OP1");
        mvc.perform(get("/api/admin/announcements").header("Authorization", op))
            .andExpect(status().isForbidden());
    }
}
