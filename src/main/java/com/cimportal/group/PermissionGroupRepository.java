package com.cimportal.group;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PermissionGroupRepository extends JpaRepository<PermissionGroup, Long> {
    Optional<PermissionGroup> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByCodeAndActiveTrue(String code);
}
