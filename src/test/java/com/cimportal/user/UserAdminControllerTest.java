package com.cimportal.user;

import com.cimportal.support.OracleIntegrationTest;
import com.cimportal.support.TestJwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class UserAdminControllerTest extends OracleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;

    @BeforeEach
    void seed() {
        users.deleteAll();
        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));
    }

    @Test
    void adminListsUsers() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void filtersUsersByDeptRoleAndQuery() throws Exception {
        String adminToken = "Bearer " + jwts.bearerFor("ADMIN1");

        // departmentCode=IT → only ADMIN1 (IT dept)
        mvc.perform(get("/api/admin/users?departmentCode=IT").header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].employeeId").value("ADMIN1"));

        // departmentCode=IT AND roleCode=OPERATOR → AND-combined, no match (ADMIN1 is PORTAL_ADMIN)
        mvc.perform(get("/api/admin/users?departmentCode=IT&roleCode=OPERATOR").header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        // q=OP → matches OP1 (employeeId contains "OP")
        mvc.perform(get("/api/admin/users?q=OP").header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].employeeId").value("OP1"));
    }

    @Test
    void operatorForbidden() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isForbidden());
    }
}
