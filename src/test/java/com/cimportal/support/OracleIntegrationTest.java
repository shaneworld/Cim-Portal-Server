package com.cimportal.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest
@ActiveProfiles("test")
public abstract class OracleIntegrationTest {
    static final OracleContainer DB = new OracleContainer("gvenzl/oracle-free:slim-faststart");
    static { DB.start(); }   // single container reused across all tests

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "oracle.jdbc.OracleDriver");
        r.add("spring.flyway.locations", () -> "classpath:db/migration/oracle");
    }
}
