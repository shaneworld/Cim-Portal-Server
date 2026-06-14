package com.cimportal.link;

import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
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
class LinkAdminControllerTest extends OracleIntegrationTest {
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
        EnumValue dev = new EnumValue(EnumCategory.LINK_ENV, "DEV", "开发环境", "DEV", 10, true);
        dev.setColor("slate");
        enums.save(dev);
        EnumValue uat = new EnumValue(EnumCategory.LINK_ENV, "UAT", "测试环境", "UAT", 20, true);
        uat.setColor("amber");
        enums.save(uat);
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
            .andExpect(jsonPath("$.environment").isEmpty())
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
    void createLinkWithEnvironmentUatReturns201() throws Exception {
        String linkBody = "{\"nameZh\":\"SPC分析\",\"nameEn\":\"SPC\"," +
            "\"url\":\"https://spc-uat.example.com\"," +
            "\"icon\":\"chart\",\"categoryCode\":\"MES\"," +
            "\"statusCode\":\"ACTIVE\",\"sortOrder\":5," +
            "\"environment\":\"UAT\"}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(linkBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.url").value("https://spc-uat.example.com"))
            .andExpect(jsonPath("$.environment").value("UAT"))
            // env presentation inlined from LINK_ENV enum_value
            .andExpect(jsonPath("$.envColor").value("amber"))
            .andExpect(jsonPath("$.envLabelEn").value("UAT"))
            .andExpect(jsonPath("$.envLabelZh").value("测试环境"));
    }

    @Test
    void createLinkWithEnvironmentDevInlinesEnvColor() throws Exception {
        String linkBody = "{\"nameZh\":\"开发\",\"nameEn\":\"Dev Link\"," +
            "\"url\":\"https://dev.example.com\"," +
            "\"icon\":\"chart\",\"categoryCode\":\"MES\"," +
            "\"statusCode\":\"ACTIVE\",\"sortOrder\":5," +
            "\"environment\":\"DEV\"}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(linkBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.environment").value("DEV"))
            .andExpect(jsonPath("$.envColor").value("slate"))
            .andExpect(jsonPath("$.envLabelEn").value("DEV"));
    }

    @Test
    void createLinkWithoutEnvironmentHasNullEnvFields() throws Exception {
        String linkBody = "{\"nameZh\":\"无环境\",\"nameEn\":\"No Env\"," +
            "\"url\":\"https://x\",\"icon\":\"factory\",\"categoryCode\":\"MES\"," +
            "\"statusCode\":\"ACTIVE\",\"sortOrder\":1}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(linkBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.environment").isEmpty())
            .andExpect(jsonPath("$.envColor").isEmpty())
            .andExpect(jsonPath("$.envLabelEn").isEmpty());
    }

    @Test
    void createLinkWithInvalidEnvironmentRejects400() throws Exception {
        String bad = "{\"nameZh\":\"x\",\"nameEn\":\"x\"," +
            "\"url\":\"https://x\"," +
            "\"icon\":\"i\",\"categoryCode\":\"MES\",\"statusCode\":\"ACTIVE\",\"sortOrder\":0," +
            "\"environment\":\"FOO\"}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missingUrlRejects400() throws Exception {
        String bad = "{\"nameZh\":\"x\",\"nameEn\":\"x\"," +
            "\"icon\":\"i\",\"categoryCode\":\"MES\",\"statusCode\":\"ACTIVE\",\"sortOrder\":0}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest());
    }

    @Test
    void blankUrlRejects400() throws Exception {
        String bad = "{\"nameZh\":\"x\",\"nameEn\":\"x\"," +
            "\"url\":\"  \"," +
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

    @Test
    void createLaunchLinkReturns201WithBothFields() throws Exception {
        String body = "{\"nameZh\":\"MES客户端\",\"nameEn\":\"MES Client\"," +
            "\"url\":\"mesclient://\",\"icon\":\"factory\",\"categoryCode\":\"MES\"," +
            "\"statusCode\":\"ACTIVE\",\"sortOrder\":70," +
            "\"launchApp\":true," +
            "\"downloadUrl\":\"https://downloads.example.com/mes-client-setup.exe\"}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.launchApp").value(true))
            .andExpect(jsonPath("$.downloadUrl").value("https://downloads.example.com/mes-client-setup.exe"));
    }

    @Test
    void createLaunchLinkWithoutDownloadUrlRejects400() throws Exception {
        String bad = "{\"nameZh\":\"MES客户端\",\"nameEn\":\"MES Client\"," +
            "\"url\":\"mesclient://\",\"icon\":\"factory\",\"categoryCode\":\"MES\"," +
            "\"statusCode\":\"ACTIVE\",\"sortOrder\":70," +
            "\"launchApp\":true}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createPlainWebLinkWithLaunchAppOmittedReturns201() throws Exception {
        String body = "{\"nameZh\":\"文档\",\"nameEn\":\"Docs\"," +
            "\"url\":\"https://docs.example.com\",\"icon\":\"book\",\"categoryCode\":\"MES\"," +
            "\"statusCode\":\"ACTIVE\",\"sortOrder\":30}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.launchApp").value(false))
            .andExpect(jsonPath("$.downloadUrl").isEmpty());
    }
}
