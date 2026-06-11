package com.cimportal.icon;

import com.cimportal.support.OracleIntegrationTest;
import com.cimportal.support.TestJwts;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Base64;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class IconControllerTest extends OracleIntegrationTest {

    /** 1x1 PNG — base64 from the plan spec */
    private static final byte[] PNG_1x1 = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    );

    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired UploadedIconRepository icons;

    String admin;

    @BeforeEach
    void seed() {
        icons.deleteAll();
        users.deleteAll();
        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        admin = "Bearer " + jwts.bearerFor("ADMIN1");
    }

    @Test
    void uploadValidPngReturns201ThenServeReturns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "icon.png", "image/png", PNG_1x1);

        String response = mvc.perform(multipart("/api/admin/icons")
                .file(file)
                .header("Authorization", admin))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ref").value(matchesPattern("upload:\\d+")))
            .andReturn().getResponse().getContentAsString();

        // Extract id from ref
        String ref = response.replaceAll(".*\"ref\":\"upload:(\\d+)\".*", "$1");
        Long id = Long.parseLong(ref.trim());

        mvc.perform(get("/api/icons/" + id))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("image/png")))
            .andExpect(content().bytes(PNG_1x1));
    }

    @Test
    void uploadWrongContentTypeReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "text.txt", "text/plain",
            "not an image".getBytes());

        mvc.perform(multipart("/api/admin/icons")
                .file(file)
                .header("Authorization", admin))
            .andExpect(status().isBadRequest());
    }

    @Test
    void uploadOversizedImageReturns400() throws Exception {
        // Create a byte array > 512KB, labelled as image/png
        byte[] bigData = new byte[513 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", bigData);

        mvc.perform(multipart("/api/admin/icons")
                .file(file)
                .header("Authorization", admin))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getMissingIconReturns404() throws Exception {
        mvc.perform(get("/api/icons/999999"))
            .andExpect(status().isNotFound());
    }
}
