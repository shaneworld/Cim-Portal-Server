package com.cimportal.auth;

import com.cimportal.support.OracleIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ArchitectureStaticResourceTest extends OracleIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void architectureHtml_isPubliclyServedWithoutAuth() throws Exception {
        mockMvc.perform(get("/architecture.html"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"));
    }
}
