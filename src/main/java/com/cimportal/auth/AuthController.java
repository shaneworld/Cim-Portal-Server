package com.cimportal.auth;

import com.cimportal.common.AppConstants;
import com.cimportal.common.error.ApiException;
import com.cimportal.common.error.ErrorCode;
import com.cimportal.setting.SecuritySettingService;
import com.cimportal.user.UserInfoRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Internal-login endpoint available on ALL profiles.
 * Issues a portal-signed JWT after verifying the employeeId exists, is active,
 * and the shared internal password matches.
 */
@RestController
@RequestMapping(AppConstants.Auth.LOGIN_PATH)
public class AuthController {

    private final UserInfoRepository users;
    private final SecuritySettingService settings;
    private final JwtEncoder portalJwtEncoder;

    public AuthController(UserInfoRepository users,
                          SecuritySettingService settings,
                          JwtEncoder portalJwtEncoder) {
        this.users          = users;
        this.settings       = settings;
        this.portalJwtEncoder = portalJwtEncoder;
    }

    @PostMapping
    public Map<String, String> login(@Valid @RequestBody AuthLoginRequest req) {
        boolean valid = users.findById(req.employeeId())
            .filter(u -> u.isActive())
            .isPresent()
            && settings.matchesInternalPassword(req.password());

        if (!valid) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "员工号或密码无效");
        }

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(AppConstants.Issuer.PORTAL)
            .subject(req.employeeId())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(AppConstants.DevToken.TTL_HOURS, ChronoUnit.HOURS))
            .build();

        String token = portalJwtEncoder
            .encode(JwtEncoderParameters.from(claims))
            .getTokenValue();

        return Map.of("access_token", token, "token_type", "Bearer");
    }

    /** Request body for POST /api/auth/login. */
    public record AuthLoginRequest(
        @NotBlank String employeeId,
        @NotBlank String password
    ) {}
}
