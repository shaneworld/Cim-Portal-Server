package com.cimportal.auth;

import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import com.cimportal.common.error.ApiException;
import com.cimportal.common.error.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal")
public class MeController {
    private final CurrentUserService currentUser;
    private final UserInfoRepository users;
    public MeController(CurrentUserService currentUser, UserInfoRepository users) {
        this.currentUser = currentUser; this.users = users;
    }

    @GetMapping("/me")
    public MeResponse me() {
        CurrentUser u = currentUser.require();   // unprovisioned→404, inactive→403
        UserInfo info = users.findById(u.employeeId())
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_PROVISIONED, "用户未配置"));
        return new MeResponse(info.getEmployeeId(), info.getDisplayNameZh(), info.getDisplayNameEn(),
            info.getDepartmentCode(), info.getRoleCode(), u.admin());
    }
}
