package com.cimportal.support;

import com.cimportal.common.AppConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class TestJwts {

    /** The portal encoder — wired by name to match the bean defined in PortalJwtKeys. */
    @Autowired JwtEncoder portalJwtEncoder;

    /**
     * Produce a Bearer token value with {@code iss=cim-portal} and {@code sub=employeeId}.
     * The portal decoder validates the issuer claim, so all test tokens must include it.
     */
    public String bearerFor(String employeeId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(AppConstants.Issuer.PORTAL)
            .subject(employeeId)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
            .build();
        return portalJwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
