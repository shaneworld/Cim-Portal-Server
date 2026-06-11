package com.cimportal.announcement;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AnnouncementControllerTest extends OracleIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired AnnouncementTypeRepository typeRepo;
    @Autowired AnnouncementRepository annoRepo;
    @Autowired SecuritySettingRepository settingRepo;
    @Autowired SecuritySettingService settingService;

    String admin;

    @BeforeEach
    void seed() {
        annoRepo.deleteAll();
        typeRepo.deleteAll();
        users.deleteAll();

        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));

        admin = "Bearer " + jwts.bearerFor("ADMIN1");

        // Reset announcements_enabled to true
        SecuritySetting s = settingRepo.findById(1L).orElseThrow();
        s.setAnnouncementsEnabled(true);
        settingRepo.save(s);
        settingService.invalidateCache();
    }

    // ── Announcement Type CRUD ────────────────────────────────────────────────

    @Test
    void createType_returns201() throws Exception {
        String body = """
            {"code":"INFO","labelZh":"通知","labelEn":"Info","color":"blue","icon":"info","sortOrder":10}
            """;
        mvc.perform(post("/api/admin/announcement-types").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("INFO"))
            .andExpect(jsonPath("$.color").value("blue"))
            .andExpect(jsonPath("$.icon").value("info"));
    }

    @Test
    void createType_duplicateCode_returns409() throws Exception {
        String body = """
            {"code":"DUP","labelZh":"重复","labelEn":"Dup","color":"blue","icon":"info","sortOrder":10}
            """;
        mvc.perform(post("/api/admin/announcement-types").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        mvc.perform(post("/api/admin/announcement-types").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
    }

    @Test
    void createType_invalidColor_returns400() throws Exception {
        String body = """
            {"code":"X","labelZh":"X","labelEn":"X","color":"pink","icon":"info","sortOrder":10}
            """;
        mvc.perform(post("/api/admin/announcement-types").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void typeList_returnsAll() throws Exception {
        typeRepo.save(new AnnouncementType("A", "A中", "A En", "blue", "info", 10, true));
        typeRepo.save(new AnnouncementType("B", "B中", "B En", "red", "info", 20, true));

        mvc.perform(get("/api/admin/announcement-types").header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updateType_changesFields() throws Exception {
        String create = """
            {"code":"UPD","labelZh":"原标签","labelEn":"Orig","color":"blue","icon":"info","sortOrder":50}
            """;
        String idStr = mvc.perform(post("/api/admin/announcement-types").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(create))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        // extract id via jsonPath
        String putBody = """
            {"code":"UPD","labelZh":"新标签","labelEn":"New","color":"amber","icon":"wrench","sortOrder":50}
            """;
        // Find the id from the created type
        long id = typeRepo.findByCode("UPD").orElseThrow().getId();
        mvc.perform(put("/api/admin/announcement-types/" + id).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(putBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.labelZh").value("新标签"))
            .andExpect(jsonPath("$.color").value("amber"));
    }

    @Test
    void deleteType_removes() throws Exception {
        AnnouncementType t = typeRepo.save(new AnnouncementType("DEL", "删", "Del", "blue", "info", 99, true));
        mvc.perform(delete("/api/admin/announcement-types/" + t.getId()).header("Authorization", admin))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/announcement-types/" + t.getId()).header("Authorization", admin))
            .andExpect(status().isNotFound());
    }

    // ── Announcement CRUD ─────────────────────────────────────────────────────

    private AnnouncementType seedType(String code, String color) {
        return typeRepo.save(new AnnouncementType(code, code + "中", code + " En", color, "info", 10, true));
    }

    private String annoBody(String typeCode) {
        return """
            {"titleZh":"标题","titleEn":"Title","bodyZh":"正文","bodyEn":"Body",
             "typeCode":"%s","pinned":false,"active":true}
            """.formatted(typeCode);
    }

    @Test
    void createAnnouncement_returns201() throws Exception {
        seedType("INFO", "blue");
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
        typeRepo.save(new AnnouncementType("INACTIVE", "停用", "Inactive", "slate", "info", 10, false));
        mvc.perform(post("/api/admin/announcements").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(annoBody("INACTIVE")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateAnnouncement_changesFields() throws Exception {
        seedType("INFO", "blue");
        seedType("MAINT", "amber");
        mvc.perform(post("/api/admin/announcements").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(annoBody("INFO")))
            .andExpect(status().isCreated());

        long id = annoRepo.findAllByOrderByPinnedDescCreatedAtDesc().get(0).getId();
        String updated = """
            {"titleZh":"更新标题","titleEn":"Updated","bodyZh":"正文","bodyEn":"Body",
             "typeCode":"MAINT","pinned":false,"active":true}
            """;
        mvc.perform(put("/api/admin/announcements/" + id).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(updated))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titleZh").value("更新标题"))
            .andExpect(jsonPath("$.typeCode").value("MAINT"))
            .andExpect(jsonPath("$.typeColor").value("amber"));
    }

    @Test
    void deleteAnnouncement_removes() throws Exception {
        seedType("INFO", "blue");
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
        seedType("INFO", "blue");
        // Save inactive announcement
        annoRepo.save(new Announcement("标题", "Title", "正文", "Body", "INFO", false, null, null, false));

        mvc.perform(get("/api/portal/announcements").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void portalEffective_excludesFutureStartsAt() throws Exception {
        seedType("INFO", "blue");
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        annoRepo.save(new Announcement("标题", "Title", "正文", "Body", "INFO", false, future, null, true));

        mvc.perform(get("/api/portal/announcements").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void portalEffective_excludesPastEndsAt() throws Exception {
        seedType("INFO", "blue");
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        annoRepo.save(new Announcement("标题", "Title", "正文", "Body", "INFO", false, null, past, true));

        mvc.perform(get("/api/portal/announcements").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void portalEffective_pinnedFirst() throws Exception {
        seedType("INFO", "blue");
        annoRepo.save(new Announcement("普通", "Normal", "正文", "Body", "INFO", false, null, null, true));
        annoRepo.save(new Announcement("置顶", "Pinned", "正文", "Body", "INFO", true, null, null, true));

        mvc.perform(get("/api/portal/announcements").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].titleEn").value("Pinned"))
            .andExpect(jsonPath("$[0].pinned").value(true));
    }

    @Test
    void portalEffective_carriesTypeColorAndIcon() throws Exception {
        typeRepo.save(new AnnouncementType("OUTAGE", "停机", "Outage", "red", "triangle-alert", 30, true));
        annoRepo.save(new Announcement("停机", "Outage", "正文", "Body", "OUTAGE", false, null, null, true));

        mvc.perform(get("/api/portal/announcements").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].typeColor").value("red"))
            .andExpect(jsonPath("$[0].typeIcon").value("triangle-alert"))
            .andExpect(jsonPath("$[0].typeLabelZh").value("停机"))
            .andExpect(jsonPath("$[0].typeLabelEn").value("Outage"));
    }

    // ── Config flag ───────────────────────────────────────────────────────────

    @Test
    void publicConfig_includesAnnouncementsEnabled() throws Exception {
        mvc.perform(get("/api/portal/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.announcementsEnabled").value(true));
    }

    @Test
    void adminPut_canChangeAnnouncementsEnabled() throws Exception {
        String req = """
            {"announcementsEnabled":false}
            """;
        mvc.perform(put("/api/admin/security-settings")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.announcementsEnabled").value(false));

        settingService.invalidateCache();
        mvc.perform(get("/api/portal/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.announcementsEnabled").value(false));
    }

    // ── Auth checks ───────────────────────────────────────────────────────────

    @Test
    void adminEndpoints_requirePortalAdmin() throws Exception {
        String op = "Bearer " + jwts.bearerFor("OP1");
        mvc.perform(get("/api/admin/announcement-types").header("Authorization", op))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/announcements").header("Authorization", op))
            .andExpect(status().isForbidden());
    }
}
