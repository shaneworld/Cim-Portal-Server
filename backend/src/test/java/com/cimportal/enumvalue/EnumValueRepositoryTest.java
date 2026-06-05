package com.cimportal.enumvalue;

import com.cimportal.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class EnumValueRepositoryTest extends MariaDbIntegrationTest {

    @Autowired EnumValueRepository repo;

    @BeforeEach
    void cleanUp() { repo.deleteAll(); }

    @Test
    void savesAndFindsActiveByCategoryOrdered() {
        repo.save(new EnumValue(EnumCategory.ROLE, "OPERATOR", "操作员", "Operator", 20, true));
        repo.save(new EnumValue(EnumCategory.ROLE, "ADMIN", "管理员", "Admin", 10, true));
        repo.save(new EnumValue(EnumCategory.ROLE, "OLD", "旧", "Old", 5, false));

        var active = repo.findByCategoryAndActiveTrueOrderBySortOrderAscIdAsc(EnumCategory.ROLE);

        assertThat(active).extracting(EnumValue::getCode).containsExactly("ADMIN", "OPERATOR");
        assertThat(repo.existsByCategoryAndCode(EnumCategory.ROLE, "OLD")).isTrue();
    }
}
