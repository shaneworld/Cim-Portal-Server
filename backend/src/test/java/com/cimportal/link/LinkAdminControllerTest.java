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
        enums.save(new EnumValue(EnumCategory.LINK_STATUS, "ACTIVE", "启用", "Active", 1, true));
        enums.save(new EnumValue(EnumCategory.ROLE, "OPERATOR", "操作员", "Operator", 1, true));
        admin = "Bearer " + jwts.bearerFor("ADMIN1");
    }

    @Test
    void createLinkThenReplaceGrants() throws Exception {
        String linkBody = "{\"code\":\"mes-wip\",\"nameZh\":\"在制品\",\"nameEn\":\"WIP\"," +
            "\"url\":\"https://x\",\"icon\":\"factory\",\"categoryCode\":\"MES\"," +
            "\"statusCode\":\"ACTIVE\",\"sortOrder\":10}";
        String id = mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(linkBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("mes-wip"))
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
    void rejectsUnknownCategory() throws Exception {
        String bad = "{\"code\":\"x\",\"nameZh\":\"x\",\"nameEn\":\"x\",\"url\":\"https://x\"," +
            "\"icon\":\"i\",\"categoryCode\":\"NOPE\",\"statusCode\":\"ACTIVE\",\"sortOrder\":0}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
