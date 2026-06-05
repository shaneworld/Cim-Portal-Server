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
                                       @RequestParam(required = false) String roleCode,
                                       @RequestParam(required = false) String q) {
        String dep = (departmentCode == null || departmentCode.isBlank()) ? null : departmentCode;
        String role = (roleCode == null || roleCode.isBlank()) ? null : roleCode;
        String query = (q == null || q.isBlank()) ? null : q;
        return users.search(dep, role, query).stream().map(UserInfoResponse::of).toList();
    }
}
