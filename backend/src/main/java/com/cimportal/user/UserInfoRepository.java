package com.cimportal.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserInfoRepository extends JpaRepository<UserInfo, String> {
    List<UserInfo> findByDepartmentCode(String departmentCode);
    List<UserInfo> findByRoleCode(String roleCode);
}
