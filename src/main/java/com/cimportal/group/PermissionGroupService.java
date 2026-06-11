package com.cimportal.group;

import com.cimportal.group.dto.GroupRequest;
import com.cimportal.group.dto.GroupResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class PermissionGroupService {
    private final PermissionGroupRepository groups;
    private final PermissionGroupMemberRepository members;

    public PermissionGroupService(PermissionGroupRepository groups, PermissionGroupMemberRepository members) {
        this.groups = groups;
        this.members = members;
    }

    public List<GroupResponse> list() {
        return groups.findAll().stream().map(GroupResponse::of).toList();
    }

    public GroupResponse get(Long id) {
        return GroupResponse.of(require(id));
    }

    @Transactional
    public GroupResponse create(GroupRequest r) {
        if (groups.existsByCode(r.code()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "组 code 已存在");
        PermissionGroup g = new PermissionGroup();
        g.setCode(r.code());
        g.setNameZh(r.nameZh());
        g.setNameEn(r.nameEn());
        g.setActive(r.active() == null || r.active());
        return GroupResponse.of(groups.save(g));
    }

    @Transactional
    public GroupResponse update(Long id, GroupRequest r) {
        PermissionGroup g = require(id);
        if (!g.getCode().equals(r.code()) && groups.existsByCode(r.code()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "组 code 已存在");
        g.setCode(r.code());
        g.setNameZh(r.nameZh());
        g.setNameEn(r.nameEn());
        if (r.active() != null) g.setActive(r.active());
        return GroupResponse.of(g);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        groups.deleteById(id);
    }

    public List<String> membersOf(Long id) {
        require(id);
        return members.findByGroupId(id).stream().map(PermissionGroupMember::getEmployeeId).toList();
    }

    @Transactional
    public List<String> replaceMembers(Long id, List<String> employeeIds) {
        require(id);
        members.deleteByGroupId(id);
        var distinct = new LinkedHashSet<>(employeeIds);
        for (String emp : distinct) members.save(new PermissionGroupMember(id, emp));
        return new ArrayList<>(distinct);
    }

    private PermissionGroup require(Long id) {
        return groups.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "组不存在"));
    }
}
