package com.cimportal.auth;

import com.cimportal.common.error.ApiException;
import com.cimportal.common.error.ErrorCode;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserInfoRepository users;
    public CurrentUserService(UserInfoRepository users) { this.users = users; }

    public String currentEmployeeId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null)
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "未认证");
        return auth.getName();
    }

    public CurrentUser require() {
        String id = currentEmployeeId();
        UserInfo u = users.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_PROVISIONED, "用户未在 user_info 中配置: " + id));
        if (!u.isActive())
            throw new ApiException(ErrorCode.USER_INACTIVE, "用户已停用: " + id);
        boolean admin = CurrentUser.ADMIN_ROLE_CODE.equals(u.getRoleCode());
        return new CurrentUser(u.getEmployeeId(), u.getDepartmentCode(), u.getRoleCode(), admin);
    }
}
