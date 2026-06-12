package com.cimportal.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OracleMigrationTest {

    @Container
    static final OracleContainer ORACLE =
        new OracleContainer("gvenzl/oracle-free:slim-faststart");

    @Test
    void oracleMigrationsApplyCleanly() {
        Flyway flyway = Flyway.configure()
            .dataSource(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword())
            .locations("classpath:db/migration/oracle")
            .load();
        MigrateResult result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(16);
    }
}
