package com.cimportal;

import com.cimportal.support.OracleIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class OpenApiDocsTest extends OracleIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void apiDocsExposeOurEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/portal/home']").exists())
            .andExpect(jsonPath("$.paths['/api/admin/links']").exists());
    }
}
