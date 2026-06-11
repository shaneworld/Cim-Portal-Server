package com.cimportal.group;

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
class PermissionGroupAdminControllerTest extends OracleIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired PermissionGroupRepository groups;
    @Autowired PermissionGroupMemberRepository members;

    String admin;

    @BeforeEach
    void seed() {
        members.deleteAll();
        groups.deleteAll();
        users.deleteAll();
        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        users.save(new UserInfo("EMP001", "员工A", "EmpA", "IT", "OPERATOR", null, true, Instant.now()));
        users.save(new UserInfo("EMP002", "员工B", "EmpB", "QA", "QA_ENGINEER", null, true, Instant.now()));
        admin = "Bearer " + jwts.bearerFor("ADMIN1");
    }

    @Test
    void createGroup_returns201() throws Exception {
        String body = "{\"code\":\"G1\",\"nameZh\":\"测试组\",\"nameEn\":\"Test Group\"}";
        mvc.perform(post("/api/admin/permission-groups").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("G1"))
            .andExpect(jsonPath("$.nameZh").value("测试组"))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createGroup_duplicateCode_returns409() throws Exception {
        String body = "{\"code\":\"DUP\",\"nameZh\":\"重复\",\"nameEn\":\"Dup\"}";
        mvc.perform(post("/api/admin/permission-groups").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        mvc.perform(post("/api/admin/permission-groups").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void listGroups() throws Exception {
        String b1 = "{\"code\":\"GA\",\"nameZh\":\"A组\",\"nameEn\":\"Group A\"}";
        String b2 = "{\"code\":\"GB\",\"nameZh\":\"B组\",\"nameEn\":\"Group B\"}";
        mvc.perform(post("/api/admin/permission-groups").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(b1))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/permission-groups").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(b2))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/admin/permission-groups").header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updateGroup_changesName() throws Exception {
        String body = "{\"code\":\"UPD\",\"nameZh\":\"原名\",\"nameEn\":\"Original\"}";
        String idStr = mvc.perform(post("/api/admin/permission-groups").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString()
            .replaceAll(".*\"id\":(\\d+).*", "$1");

        String updated = "{\"code\":\"UPD\",\"nameZh\":\"新名称\",\"nameEn\":\"Updated Name\"}";
        mvc.perform(put("/api/admin/permission-groups/" + idStr).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(updated))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nameEn").value("Updated Name"));
    }

    @Test
    void putMembers_thenGetMembers() throws Exception {
        // Create group
        String body = "{\"code\":\"MBR\",\"nameZh\":\"成员组\",\"nameEn\":\"Member Group\"}";
        String idStr = mvc.perform(post("/api/admin/permission-groups").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString()
            .replaceAll(".*\"id\":(\\d+).*", "$1");

        // Replace members — no FK constraint on employee_id, any string is valid
        String membersBody = "{\"employeeIds\":[\"EMP001\",\"EMP002\"]}";
        mvc.perform(put("/api/admin/permission-groups/" + idStr + "/members").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(membersBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        // GET members
        mvc.perform(get("/api/admin/permission-groups/" + idStr + "/members").header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        // Replace with single member
        mvc.perform(put("/api/admin/permission-groups/" + idStr + "/members").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"employeeIds\":[\"EMP001\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0]").value("EMP001"));
    }

    @Test
    void deleteGroup_returns204() throws Exception {
        String body = "{\"code\":\"DEL\",\"nameZh\":\"删除组\",\"nameEn\":\"Delete Group\"}";
        String idStr = mvc.perform(post("/api/admin/permission-groups").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString()
            .replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(delete("/api/admin/permission-groups/" + idStr).header("Authorization", admin))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/permission-groups/" + idStr).header("Authorization", admin))
            .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/admin/permission-groups"))
            .andExpect(status().isUnauthorized());
    }
}
