package com.cimportal.auth;

import com.cimportal.setting.SecuritySettingService;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * After authentication, resolves the employeeId from the JWT and looks up
 * user_info to grant ROLE_PORTAL_ADMIN where applicable.
 * <p>
 * EmployeeId resolution order:
 * <ol>
 *   <li>The claim named by {@code security_setting.sso_username_claim}
 *       (e.g. {@code preferred_username} for Keycloak)</li>
 *   <li>Falls back to {@code sub} — used by portal-internal tokens which have
 *       no extra username claim.</li>
 * </ol>
 */
@Component
public class UserInfoAuthoritiesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserInfoRepository users;
    private final SecuritySettingService settings;

    public UserInfoAuthoritiesConverter(UserInfoRepository users,
                                        SecuritySettingService settings) {
        this.users    = users;
        this.settings = settings;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String employeeId = resolveEmployeeId(jwt);
        List<GrantedAuthority> authorities = new ArrayList<>();
        users.findById(employeeId)
             .filter(UserInfo::isActive)
             .filter(u -> CurrentUser.ADMIN_ROLE_CODE.equals(u.getRoleCode()))
             .ifPresent(u -> authorities.add(new SimpleGrantedAuthority("ROLE_PORTAL_ADMIN")));
        return new JwtAuthenticationToken(jwt, authorities, employeeId);
    }

    private String resolveEmployeeId(Jwt jwt) {
        try {
            String claim = settings.get().getSsoUsernameClaim();
            if (claim != null && !claim.isBlank()) {
                String val = jwt.getClaimAsString(claim);
                if (val != null && !val.isBlank()) return val;
            }
        } catch (Exception ignored) {
            // fall through to sub
        }
        return jwt.getSubject();
    }
}
