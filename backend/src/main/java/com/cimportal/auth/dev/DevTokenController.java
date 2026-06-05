package com.cimportal.auth.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** dev only: exchange an employeeId for a locally-signed JWT (no real IdP). */
@RestController
@Profile("dev")
public class DevTokenController {
    private final JwtEncoder encoder;
    public DevTokenController(JwtEncoder encoder) { this.encoder = encoder; }

    @GetMapping("/dev/token")
    public Map<String, String> token(@RequestParam String employeeId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(employeeId)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(12, ChronoUnit.HOURS))
            .build();
        String token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return Map.of("access_token", token, "token_type", "Bearer");
    }
}
