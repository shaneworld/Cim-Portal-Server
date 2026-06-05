package com.cimportal.auth;

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

/** After authentication, look up user_info by sub and grant ROLE_PORTAL_ADMIN per role_code. */
@Component
public class UserInfoAuthoritiesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserInfoRepository users;
    public UserInfoAuthoritiesConverter(UserInfoRepository users) { this.users = users; }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        String employeeId = jwt.getSubject();
        users.findById(employeeId)
             .filter(UserInfo::isActive)
             .filter(u -> CurrentUser.ADMIN_ROLE_CODE.equals(u.getRoleCode()))
             .ifPresent(u -> authorities.add(new SimpleGrantedAuthority("ROLE_PORTAL_ADMIN")));
        return new JwtAuthenticationToken(jwt, authorities, employeeId);
    }
}
