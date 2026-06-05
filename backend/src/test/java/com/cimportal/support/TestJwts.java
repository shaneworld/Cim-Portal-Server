package com.cimportal.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class TestJwts {
    @Autowired JwtEncoder encoder;

    /** Produce a Bearer token value with sub=employeeId for MockMvc Authorization headers. */
    public String bearerFor(String employeeId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(employeeId).issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS)).build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
