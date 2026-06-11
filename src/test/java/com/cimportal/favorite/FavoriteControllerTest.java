package com.cimportal.favorite;

import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.link.*;
import com.cimportal.link.LinkTestFactory;
import com.cimportal.support.OracleIntegrationTest;
import com.cimportal.support.TestJwts;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class FavoriteControllerTest extends OracleIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired EnumValueRepository enums;
    @Autowired LinkRepository links;
    @Autowired LinkAccessGrantRepository grants;
    @Autowired FavoriteRepository favorites;

    String operator;   // OP1 — role OPERATOR

    @BeforeEach
    void seed() {
        favorites.deleteAll();
        grants.deleteAll();
        links.deleteAll();
        users.deleteAll();
        enums.deleteAll();

        enums.save(new EnumValue(EnumCategory.LINK_CATEGORY, "MES", "制造执行", "MES", 1, true));
        enums.save(new EnumValue(EnumCategory.LINK_STATUS, "ACTIVE", "启用", "Active", 1, true));

        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));

        operator = "Bearer " + jwts.bearerFor("OP1");
    }

    // ── Happy path: accessible link ───────────────────────────────────────────

    @Test
    void postFavorite_accessibleLink_returns204_thenHomeShowsFavoriteTrue() throws Exception {
        // Accessible link: no grants (open to everyone)
        Link open = LinkTestFactory.newLink("Open Link", "MES");
        links.save(open);

        // POST → 204
        mvc.perform(post("/api/portal/favorites/" + open.getId())
                .header("Authorization", operator))
            .andExpect(status().isNoContent());

        // GET /home → that link has favorite=true
        mvc.perform(get("/api/portal/home").header("Authorization", operator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[0].links[0].nameEn").value("Open Link"))
            .andExpect(jsonPath("$.categories[0].links[0].favorite").value(true));
    }

    @Test
    void deleteFavorite_removesIt_homeShowsFavoriteFalse() throws Exception {
        Link open = LinkTestFactory.newLink("Open Link", "MES");
        links.save(open);

        // Add favorite
        mvc.perform(post("/api/portal/favorites/" + open.getId())
                .header("Authorization", operator))
            .andExpect(status().isNoContent());

        // Confirm it's there
        mvc.perform(get("/api/portal/home").header("Authorization", operator))
            .andExpect(jsonPath("$.categories[0].links[0].favorite").value(true));

        // DELETE → 204
        mvc.perform(delete("/api/portal/favorites/" + open.getId())
                .header("Authorization", operator))
            .andExpect(status().isNoContent());

        // Confirm it's gone
        mvc.perform(get("/api/portal/home").header("Authorization", operator))
            .andExpect(jsonPath("$.categories[0].links[0].favorite").value(false));
    }

    @Test
    void postFavorite_idempotent_noErrorOnDuplicate() throws Exception {
        Link open = LinkTestFactory.newLink("Open Link", "MES");
        links.save(open);

        mvc.perform(post("/api/portal/favorites/" + open.getId())
                .header("Authorization", operator))
            .andExpect(status().isNoContent());

        // Second POST — should not throw, still 204
        mvc.perform(post("/api/portal/favorites/" + open.getId())
                .header("Authorization", operator))
            .andExpect(status().isNoContent());

        // Only one favorite row exists
        assertThat(favorites.findLinkIdsByEmployeeId("OP1")).hasSize(1);
    }

    // ── Access-denied: restricted link ────────────────────────────────────────

    @Test
    void postFavorite_restrictedLink_returns403_andNoRowCreated() throws Exception {
        // Restricted link: ROLE grant for QA_ENGINEER — OP1 has OPERATOR so no match
        Link restricted = LinkTestFactory.newLink("QA Only", "MES");
        links.save(restricted);
        grants.save(new LinkAccessGrant(restricted.getId(), GrantType.ROLE, "QA_ENGINEER"));

        // POST → 403
        mvc.perform(post("/api/portal/favorites/" + restricted.getId())
                .header("Authorization", operator))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // No favorite row was created
        assertThat(favorites.existsByEmployeeIdAndLinkId("OP1", restricted.getId())).isFalse();

        // /home shows the link with favorite=false (and accessible=false — url hidden)
        mvc.perform(get("/api/portal/home").header("Authorization", operator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[0].links[0].nameEn").value("QA Only"))
            .andExpect(jsonPath("$.categories[0].links[0].accessible").value(false))
            .andExpect(jsonPath("$.categories[0].links[0].favorite").value(false));
    }

    // ── 404 on missing link ───────────────────────────────────────────────────

    @Test
    void postFavorite_missingLink_returns404() throws Exception {
        mvc.perform(post("/api/portal/favorites/999999")
                .header("Authorization", operator))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ── Auth guard ────────────────────────────────────────────────────────────

    @Test
    void postFavorite_unauthenticated_returns401() throws Exception {
        mvc.perform(post("/api/portal/favorites/1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteFavorite_unauthenticated_returns401() throws Exception {
        mvc.perform(delete("/api/portal/favorites/1"))
            .andExpect(status().isUnauthorized());
    }
}
