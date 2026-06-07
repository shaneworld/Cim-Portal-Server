package com.cimportal.auth;

import com.cimportal.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CorsConfigTest extends MariaDbIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void preflightFromTauriOriginIsAllowed() throws Exception {
        mockMvc.perform(options("/api/portal/home")
                .header("Origin", "tauri://localhost")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "tauri://localhost"));
    }

    @Test
    void preflightFromDisallowedOriginHasNoAcao() throws Exception {
        mockMvc.perform(options("/api/portal/home")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
