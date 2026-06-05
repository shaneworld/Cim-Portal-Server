package com.cimportal.enumvalue;

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
class EnumAdminControllerTest extends MariaDbIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired EnumValueRepository enums;

    @BeforeEach
    void seedUsers() {
        users.deleteAll(); enums.deleteAll();
        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));
    }

    @Test
    void adminCreatesEnum_thenDuplicateConflicts() throws Exception {
        String admin = "Bearer " + jwts.bearerFor("ADMIN1");
        String body = "{\"code\":\"FAB1-PROD\",\"labelZh\":\"一厂生产\",\"labelEn\":\"FAB1\",\"sortOrder\":10}";

        mvc.perform(post("/api/admin/enums/DEPARTMENT").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("FAB1-PROD"));

        mvc.perform(post("/api/admin/enums/DEPARTMENT").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
    }

    @Test
    void invalidCategoryPath_returnsStructured400() throws Exception {
        String admin = "Bearer " + jwts.bearerFor("ADMIN1");
        mvc.perform(get("/api/admin/enums/BOGUS").header("Authorization", admin))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void nonAdminForbidden_anonymousUnauthorized() throws Exception {
        String op = "Bearer " + jwts.bearerFor("OP1");
        String body = "{\"code\":\"X\",\"labelZh\":\"x\",\"labelEn\":\"x\",\"sortOrder\":0}";

        mvc.perform(post("/api/admin/enums/ROLE").header("Authorization", op)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/enums/ROLE")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
    }
}
