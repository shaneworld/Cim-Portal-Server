package com.cimportal.quicklink;

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
class QuickLinkControllerTest extends OracleIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired QuickLinkRepository quickLinkRepo;

    String admin;
    String operator;

    @BeforeEach
    void seed() {
        quickLinkRepo.deleteAll();
        users.deleteAll();

        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));

        admin = "Bearer " + jwts.bearerFor("ADMIN1");
        operator = "Bearer " + jwts.bearerFor("OP1");
    }

    // ── Admin CRUD ────────────────────────────────────────────────────────────

    @Test
    void adminCreate_returns201() throws Exception {
        String body = """
            {"labelZh":"门户","labelEn":"Portal","url":"https://example.com","icon":"link","sortOrder":10,"active":true}
            """;
        mvc.perform(post("/api/admin/quick-links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.labelZh").value("门户"))
            .andExpect(jsonPath("$.labelEn").value("Portal"))
            .andExpect(jsonPath("$.url").value("https://example.com"))
            .andExpect(jsonPath("$.icon").value("link"))
            .andExpect(jsonPath("$.sortOrder").value(10))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void adminCreate_missingFields_returns400() throws Exception {
        String body = """
            {"labelZh":"门户"}
            """;
        mvc.perform(post("/api/admin/quick-links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void adminList_returnsAllInSortOrder() throws Exception {
        quickLinkRepo.save(new QuickLink("B链接", "Link B", "https://b.example.com", null, 20, true));
        quickLinkRepo.save(new QuickLink("A链接", "Link A", "https://a.example.com", null, 10, true));
        quickLinkRepo.save(new QuickLink("C链接", "Link C", "https://c.example.com", null, 30, false));

        mvc.perform(get("/api/admin/quick-links").header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].labelEn").value("Link A"))
            .andExpect(jsonPath("$[1].labelEn").value("Link B"))
            .andExpect(jsonPath("$[2].labelEn").value("Link C"));
    }

    @Test
    void adminGetById_returnsEntry() throws Exception {
        QuickLink link = quickLinkRepo.save(new QuickLink("安全", "Security", "https://sec.example.com", null, 5, true));

        mvc.perform(get("/api/admin/quick-links/" + link.getId()).header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("https://sec.example.com"));
    }

    @Test
    void adminGetById_notFound_returns404() throws Exception {
        mvc.perform(get("/api/admin/quick-links/99999").header("Authorization", admin))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminUpdate_changesFields() throws Exception {
        QuickLink link = quickLinkRepo.save(new QuickLink("旧名", "Old", "https://old.example.com", null, 50, true));

        String updated = """
            {"labelZh":"新名","labelEn":"New","url":"https://new.example.com","sortOrder":5,"active":false}
            """;
        mvc.perform(put("/api/admin/quick-links/" + link.getId()).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(updated))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.labelZh").value("新名"))
            .andExpect(jsonPath("$.url").value("https://new.example.com"))
            .andExpect(jsonPath("$.sortOrder").value(5))
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void adminUpdate_active_false_removesFromPortal() throws Exception {
        QuickLink link = quickLinkRepo.save(new QuickLink("可见", "Visible", "https://v.example.com", null, 10, true));

        mvc.perform(get("/api/portal/quick-links").header("Authorization", operator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        String updated = """
            {"labelZh":"可见","labelEn":"Visible","url":"https://v.example.com","active":false}
            """;
        mvc.perform(put("/api/admin/quick-links/" + link.getId()).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(updated))
            .andExpect(status().isOk());

        mvc.perform(get("/api/portal/quick-links").header("Authorization", operator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void adminDelete_removes() throws Exception {
        QuickLink link = quickLinkRepo.save(new QuickLink("删除", "Del", "https://del.example.com", null, 100, true));

        mvc.perform(delete("/api/admin/quick-links/" + link.getId()).header("Authorization", admin))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/quick-links/" + link.getId()).header("Authorization", admin))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminEndpoints_requirePortalAdmin() throws Exception {
        mvc.perform(get("/api/admin/quick-links").header("Authorization", operator))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/quick-links").header("Authorization", operator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"labelZh\":\"X\",\"labelEn\":\"X\",\"url\":\"https://x.example.com\"}"))
            .andExpect(status().isForbidden());
    }

    // ── Portal: active ordered list ───────────────────────────────────────────

    @Test
    void portalQuickLinks_returnsOnlyActiveInSortOrder() throws Exception {
        quickLinkRepo.save(new QuickLink("B运营", "Ops B", "https://b.example.com", null, 20, true));
        quickLinkRepo.save(new QuickLink("A运营", "Ops A", "https://a.example.com", null, 10, true));
        quickLinkRepo.save(new QuickLink("停用", "Inactive", "https://i.example.com", null, 5, false));

        mvc.perform(get("/api/portal/quick-links").header("Authorization", operator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].labelEn").value("Ops A"))
            .andExpect(jsonPath("$[1].labelEn").value("Ops B"));
    }

    @Test
    void portalQuickLinks_excludesInactive() throws Exception {
        quickLinkRepo.save(new QuickLink("停用", "Inactive", "https://i.example.com", null, 1, false));

        mvc.perform(get("/api/portal/quick-links").header("Authorization", operator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void portalQuickLinks_requiresAuth() throws Exception {
        mvc.perform(get("/api/portal/quick-links"))
            .andExpect(status().isUnauthorized());
    }
}
