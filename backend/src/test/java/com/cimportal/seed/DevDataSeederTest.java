package com.cimportal.seed;

import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.link.LinkAccessGrantRepository;
import com.cimportal.link.LinkRepository;
import com.cimportal.support.MariaDbIntegrationTest;
import com.cimportal.user.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DevDataSeederTest extends MariaDbIntegrationTest {
    @Autowired EnumValueRepository enums;
    @Autowired LinkRepository links;
    @Autowired LinkAccessGrantRepository grants;
    @Autowired UserInfoRepository users;

    @BeforeEach
    void clear() {
        grants.deleteAll(); links.deleteAll();
        enums.deleteAll(); users.deleteAll();
    }

    @Test
    void seedsEmptyDatabaseIdempotently() throws Exception {
        DevDataSeeder seeder = new DevDataSeeder(enums, links, grants, users);

        seeder.run(null);
        long enumCount = enums.count();
        assertThat(enumCount).isEqualTo(12);
        assertThat(users.count()).isEqualTo(4);
        assertThat(links.count()).isEqualTo(6);
        assertThat(grants.count()).isEqualTo(2);

        // second run must be a no-op (idempotent): counts unchanged
        seeder.run(null);
        assertThat(enums.count()).isEqualTo(enumCount);
        assertThat(users.count()).isEqualTo(4);
        assertThat(links.count()).isEqualTo(6);
    }
}
