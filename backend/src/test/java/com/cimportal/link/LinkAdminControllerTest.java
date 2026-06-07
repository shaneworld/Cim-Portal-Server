package com.cimportal.link;

import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
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
class LinkAdminControllerTest extends MariaDbIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired EnumValueRepository enums;
    @Autowired LinkRepository links;

    String admin;

    @BeforeEach
    void seed() {
        users.deleteAll(); enums.deleteAll(); links.deleteAll();
        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        enums.save(new EnumValue(EnumCategory.LINK_CATEGORY, "MES", "制造执行", "MES", 1, true));
        enums.save(new EnumValue(EnumCategory.LINK_CATEGORY, "QUALITY", "质量", "Quality", 2, true));
        enums.save(new EnumValue(EnumCategory.LINK_STATUS, "ACTIVE", "启用", "Active", 1, true));
        enums.save(new EnumValue(EnumCategory.ROLE, "OPERATOR", "操作员", "Operator", 1, true));
        admin = "Bearer " + jwts.bearerFor("ADMIN1");
    }

    @Test
    void createPlainUrlLinkThenReplaceGrants() throws Exception {
        String linkBody = "{\"nameZh\":\"在制品\",\"nameEn\":\"WIP\"," +
            "\"url\":\"https://x\",\"icon\":\"factory\",\"categoryCode\":\"MES\"," +
            "\"statusCode\":\"ACTIVE\",\"sortOrder\":10}";
        String id = mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(linkBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.url").value("https://x"))
            .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");

        String grantsBody = "{\"grants\":[{\"grantType\":\"ROLE\",\"grantCode\":\"OPERATOR\"}]}";
        mvc.perform(put("/api/admin/links/" + id + "/grants").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(grantsBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].grantCode").value("OPERATOR"));

        mvc.perform(put("/api/admin/links/" + id + "/grants").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"grants\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createEnvAwareLinkReturns201() throws Exception {
        String linkBody = "{\"nameZh\":\"SPC分析\",\"nameEn\":\"SPC\"," +
            "\"urlDev\":\"https://spc-dev.example.com\"," +
            "\"urlUat\":\"https://spc-uat.example.com\"," +
            "\"urlRelease\":\"https://spc.example.com\"," +
            "\"icon\":\"chart\",\"categoryCode\":\"MES\"," +
            "\"statusCode\":\"ACTIVE\",\"sortOrder\":5}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(linkBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.urlDev").value("https://spc-dev.example.com"))
            .andExpect(jsonPath("$.urlUat").value("https://spc-uat.example.com"))
            .andExpect(jsonPath("$.urlRelease").value("https://spc.example.com"))
            .andExpect(jsonPath("$.url").doesNotExist());
    }

    @Test
    void partialEnvUrlsReject400() throws Exception {
        // Only one env URL provided — should fail validation
        String bad = "{\"nameZh\":\"x\",\"nameEn\":\"x\"," +
            "\"urlDev\":\"https://dev.x\"," +
            "\"icon\":\"i\",\"categoryCode\":\"MES\",\"statusCode\":\"ACTIVE\",\"sortOrder\":0}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest());
    }

    @Test
    void bothUrlAndEnvUrlsReject400() throws Exception {
        // url + all three env URLs → invalid (XOR)
        String bad = "{\"nameZh\":\"x\",\"nameEn\":\"x\"," +
            "\"url\":\"https://x\"," +
            "\"urlDev\":\"https://dev.x\",\"urlUat\":\"https://uat.x\",\"urlRelease\":\"https://rel.x\"," +
            "\"icon\":\"i\",\"categoryCode\":\"MES\",\"statusCode\":\"ACTIVE\",\"sortOrder\":0}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest());
    }

    @Test
    void noUrlAtAllRejects400() throws Exception {
        // Neither url nor env URLs → invalid
        String bad = "{\"nameZh\":\"x\",\"nameEn\":\"x\"," +
            "\"icon\":\"i\",\"categoryCode\":\"MES\",\"statusCode\":\"ACTIVE\",\"sortOrder\":0}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest());
    }

    @Test
    void filtersLinksByCategoryAndQuery() throws Exception {
        // Create MES link
        String mesBody = "{\"nameZh\":\"在制品\",\"nameEn\":\"WIP\"," +
            "\"url\":\"https://mes\",\"icon\":\"factory\",\"categoryCode\":\"MES\"," +
            "\"statusCode\":\"ACTIVE\",\"sortOrder\":10}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(mesBody))
            .andExpect(status().isCreated());

        // Create QUALITY link
        String qualityBody = "{\"nameZh\":\"质量看板\",\"nameEn\":\"Quality Dashboard\"," +
            "\"url\":\"https://quality\",\"icon\":\"chart\",\"categoryCode\":\"QUALITY\"," +
            "\"statusCode\":\"ACTIVE\",\"sortOrder\":20}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(qualityBody))
            .andExpect(status().isCreated());

        // Filter by categoryCode=MES → only 1 result
        mvc.perform(get("/api/admin/links?categoryCode=MES").header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].nameEn").value("WIP"));

        // Filter by q=wip (substring match on nameEn) → only MES link
        mvc.perform(get("/api/admin/links?q=wip").header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].nameEn").value("WIP"));

        // No filters → both results
        mvc.perform(get("/api/admin/links").header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void rejectsUnknownCategory() throws Exception {
        String bad = "{\"nameZh\":\"x\",\"nameEn\":\"x\",\"url\":\"https://x\"," +
            "\"icon\":\"i\",\"categoryCode\":\"NOPE\",\"statusCode\":\"ACTIVE\",\"sortOrder\":0}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
