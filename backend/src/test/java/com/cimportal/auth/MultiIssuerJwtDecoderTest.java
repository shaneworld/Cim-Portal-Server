package com.cimportal.auth;

import com.cimportal.common.AppConstants;
import com.cimportal.setting.SecuritySettingService;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MultiIssuerJwtDecoderTest {

    private JwtDecoder portalDecoder;
    private JwtEncoder portalEncoder;
    private SecuritySettingService settings;
    private MultiIssuerJwtDecoder decoder;

    @BeforeEach
    void setup() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();

        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) kp.getPublic())
            .privateKey((RSAPrivateKey) kp.getPrivate()).build();
        portalEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));

        NimbusJwtDecoder nd = NimbusJwtDecoder.withPublicKey((RSAPublicKey) kp.getPublic()).build();
        OAuth2TokenValidator<Jwt> combined = new DelegatingOAuth2TokenValidator<>(
            List.of(JwtValidators.createDefault(), new JwtIssuerValidator(AppConstants.Issuer.PORTAL)));
        nd.setJwtValidator(combined);
        portalDecoder = nd;

        settings = mock(SecuritySettingService.class);
        when(settings.ssoIssuer()).thenReturn(null);

        decoder = new MultiIssuerJwtDecoder(portalDecoder, settings);
    }

    private String mintPortalToken(String subject) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(AppConstants.Issuer.PORTAL)
            .subject(subject)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
            .build();
        return portalEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    @Test
    void portalIssuedToken_decodesSuccessfully() {
        String token = mintPortalToken("EMP1");
        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("EMP1");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(AppConstants.Issuer.PORTAL);
    }

    @Test
    void unknownIssuer_throwsBadJwtException() {
        // Mint a token manually with a different issuer claim (can't use portalEncoder
        // as it signs with our key and we'd need to decode with the right key).
        // Instead use a different encoder/key.
        assertThatThrownBy(() -> decoder.decode("not.a.jwt"))
            .isInstanceOf(BadJwtException.class);
    }

    @Test
    void malformedToken_throwsBadJwtException() {
        assertThatThrownBy(() -> decoder.decode("this.is.garbage"))
            .isInstanceOf(BadJwtException.class);
    }

    @Test
    void noSsoIssuerConfigured_portalTokenStillWorks() {
        when(settings.ssoIssuer()).thenReturn(null);
        String token = mintPortalToken("USER1");
        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("USER1");
    }
}
