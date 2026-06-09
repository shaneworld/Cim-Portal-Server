package com.cimportal.auth;

import com.cimportal.common.AppConstants;
import com.cimportal.setting.SecuritySettingService;
import com.nimbusds.jwt.JWTParser;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Routes JWT validation to the correct decoder based on the {@code iss} claim:
 * <ul>
 *   <li>{@code iss=cim-portal} → portal RSA decoder (local key, always available)</li>
 *   <li>{@code iss=<configured SSO issuer>} → JWKS-backed NimbusJwtDecoder built lazily from the
 *       issuer location; cached until the issuer URI changes in the DB</li>
 *   <li>anything else → {@link BadJwtException}</li>
 * </ul>
 */
@Component
public class MultiIssuerJwtDecoder implements JwtDecoder {

    private final JwtDecoder portalDecoder;
    private final SecuritySettingService settings;

    /** Cached (issuerUri, decoder) pair — rebuilt only when the issuer changes. */
    private volatile CachedSsoDecoder ssoCache;

    public MultiIssuerJwtDecoder(JwtDecoder portalJwtDecoder,
                                 SecuritySettingService settings) {
        this.portalDecoder = portalJwtDecoder;
        this.settings       = settings;
    }

    @Override
    public Jwt decode(String token) {
        String iss;
        try {
            iss = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
        } catch (Exception e) {
            throw new BadJwtException("Malformed JWT token: " + e.getMessage(), e);
        }

        if (AppConstants.Issuer.PORTAL.equals(iss)) {
            return portalDecoder.decode(token);
        }

        String ssoIssuer = settings.ssoIssuer();
        if (ssoIssuer != null && !ssoIssuer.isBlank() && ssoIssuer.equals(iss)) {
            return ssoDecoder(ssoIssuer).decode(token);
        }

        throw new BadJwtException("Untrusted issuer: " + iss);
    }

    // ── SSO decoder cache ─────────────────────────────────────────────────────

    private JwtDecoder ssoDecoder(String issuerUri) {
        CachedSsoDecoder cached = ssoCache;
        if (cached != null && issuerUri.equals(cached.issuerUri)) {
            return cached.decoder;
        }
        return rebuildSsoDecoder(issuerUri);
    }

    private synchronized JwtDecoder rebuildSsoDecoder(String issuerUri) {
        // double-checked locking
        CachedSsoDecoder cached = ssoCache;
        if (cached != null && issuerUri.equals(cached.issuerUri)) {
            return cached.decoder;
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        ssoCache = new CachedSsoDecoder(issuerUri, decoder);
        return decoder;
    }

    private record CachedSsoDecoder(String issuerUri, JwtDecoder decoder) {}
}
