package com.cimportal.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface PermissionGroupMemberRepository extends JpaRepository<PermissionGroupMember, PermissionGroupMemberId> {
    List<PermissionGroupMember> findByGroupId(Long groupId);
    void deleteByGroupId(Long groupId);

    /** Returns the active group codes for the given employee. */
    @Query("select g.code from PermissionGroup g, PermissionGroupMember m " +
           "where m.groupId = g.id and m.employeeId = ?1 and g.active = true")
    List<String> findActiveGroupCodesByEmployeeId(String employeeId);
}
