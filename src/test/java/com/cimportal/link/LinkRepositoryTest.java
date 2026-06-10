package com.cimportal.link;

import com.cimportal.support.OracleIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class LinkRepositoryTest extends OracleIntegrationTest {
    @Autowired LinkRepository links;
    @Autowired LinkAccessGrantRepository grants;

    @BeforeEach
    void cleanUp() { grants.deleteAll(); links.deleteAll(); }

    @Test
    void cascadeDeleteRemovesGrants() {
        Link l = links.save(newLink("WIP Management"));
        grants.save(new LinkAccessGrant(l.getId(), GrantType.ROLE, "OPERATOR"));
        assertThat(grants.findByLinkId(l.getId())).hasSize(1);

        links.deleteById(l.getId());
        links.flush();

        assertThat(grants.findByLinkId(l.getId())).isEmpty();
    }

    private Link newLink(String nameEn) {
        Link l = new Link();
        l.setNameZh("名"); l.setNameEn(nameEn);
        l.setUrl("https://x"); l.setIcon("factory");
        l.setCategoryCode("MES"); l.setStatusCode("ACTIVE");
        l.setSortOrder(1); l.setOpenInNewTab(true);
        return l;
    }
}
