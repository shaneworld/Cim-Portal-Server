package com.cimportal.user;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {
    private final UserInfoRepository users;
    public UserAdminController(UserInfoRepository users) { this.users = users; }

    @GetMapping
    public List<UserInfoResponse> list(@RequestParam(required = false) String departmentCode,
                                       @RequestParam(required = false) String roleCode) {
        List<UserInfo> result;
        if (departmentCode != null) result = users.findByDepartmentCode(departmentCode);
        else if (roleCode != null) result = users.findByRoleCode(roleCode);
        else result = users.findAll();
        return result.stream().map(UserInfoResponse::of).toList();
    }
}
