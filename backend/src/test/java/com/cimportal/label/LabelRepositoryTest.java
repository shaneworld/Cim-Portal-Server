package com.cimportal.label;

import com.cimportal.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class LabelRepositoryTest extends MariaDbIntegrationTest {
    @Autowired LabelRepository repo;

    @BeforeEach
    void cleanUp() { repo.deleteAll(); }

    @Test
    void savesAndChecksKey() {
        repo.save(new Label("portal.title", "SYSTEM_NAME", "门户", "Portal"));
        assertThat(repo.existsByLabelKey("portal.title")).isTrue();
        assertThat(repo.findByType("SYSTEM_NAME")).hasSize(1);
    }
}
