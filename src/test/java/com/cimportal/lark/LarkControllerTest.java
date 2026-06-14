package com.cimportal.lark;

import com.cimportal.link.Link;
import com.cimportal.link.LinkRepository;
import com.cimportal.support.OracleIntegrationTest;
import com.cimportal.support.TestJwts;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class LarkControllerTest extends OracleIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired LinkRepository links;

    @MockBean LarkService lark;   // no real Lark calls

    String operator;
    Long linkId;

    @BeforeEach
    void seed() {
        links.deleteAll();
        users.deleteAll();
        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));
        operator = "Bearer " + jwts.bearerFor("OP1");
        Link l = links.save(new Link("MES系统", "MES", "https://x", "factory", "MES", "ACTIVE", 1, true));
        linkId = l.getId();
        reset(lark);
    }

    @Test
    void accessRequest_requiresAuth() throws Exception {
        mvc.perform(post("/api/portal/access-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkId\":" + linkId + "}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void accessRequest_unknownLink_returns404() throws Exception {
        mvc.perform(post("/api/portal/access-requests").header("Authorization", operator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkId\":99999,\"reason\":\"x\"}"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(lark);
    }

    @Test
    void accessRequest_valid_returns200AndCallsService() throws Exception {
        mvc.perform(post("/api/portal/access-requests").header("Authorization", operator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkId\":" + linkId + ",\"reason\":\"需要权限\"}"))
            .andExpect(status().isOk());
        verify(lark).sendAccessRequest(any(), any(), eq("需要权限"));
    }

    @Test
    void feedback_requiresAuth() throws Exception {
        mvc.perform(post("/api/portal/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hi\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void feedback_blankMessage_returns400() throws Exception {
        mvc.perform(post("/api/portal/feedback").header("Authorization", operator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"  \"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(lark);
    }

    @Test
    void feedback_valid_returns200AndCallsService() throws Exception {
        mvc.perform(post("/api/portal/feedback").header("Authorization", operator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"门户很好用\"}"))
            .andExpect(status().isOk());
        verify(lark).sendFeedback(any(), eq("门户很好用"));
    }
}
