package com.cimportal.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserInfoRepository extends JpaRepository<UserInfo, String> {
    List<UserInfo> findByDepartmentCode(String departmentCode);
    List<UserInfo> findByRoleCode(String roleCode);

    @Query("""
        SELECT u FROM UserInfo u
        WHERE (:departmentCode IS NULL OR u.departmentCode = :departmentCode)
          AND (:roleCode IS NULL OR u.roleCode = :roleCode)
          AND (:q IS NULL OR LOWER(u.employeeId) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(u.displayNameZh) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(u.displayNameEn) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY u.employeeId ASC
        """)
    List<UserInfo> search(@Param("departmentCode") String departmentCode,
                         @Param("roleCode") String roleCode,
                         @Param("q") String q);
}
