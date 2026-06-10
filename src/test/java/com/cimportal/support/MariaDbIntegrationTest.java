package com.cimportal.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest
@ActiveProfiles("test")
public abstract class MariaDbIntegrationTest {

    static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11.4");

    static { DB.start(); }   // single container reused across all tests

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
        r.add("spring.flyway.locations", () -> "classpath:db/migration/mariadb");
    }
}
