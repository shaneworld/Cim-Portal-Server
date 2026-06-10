package com.cimportal.auth;

import com.cimportal.common.AppConstants;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Produces the portal-internal RSA key pair used to mint / verify {@code iss=cim-portal} JWTs.
 * <p>
 * Replaces the old {@code @Profile(dev,test)} devKeyPair/devJwtDecoder/devJwtEncoder beans
 * in SecurityConfig — this works across ALL profiles.
 * <p>
 * If both {@code app.security.portal-jwt.private-key} and {@code .public-key} are present
 * (PEM-encoded DER, no headers required) the configured keys are used; otherwise an ephemeral
 * 2048-bit RSA key is generated at startup (only appropriate for dev/test — a WARN is logged
 * in all other cases).
 */
@Configuration
public class PortalJwtKeys {

    private static final Logger log = LoggerFactory.getLogger(PortalJwtKeys.class);

    @Value("${app.security.portal-jwt.private-key:}") private String privateKeyPem;
    @Value("${app.security.portal-jwt.public-key:}")  private String publicKeyPem;

    @Value("${app.security.portal-jwt.private-key-location:}") private String privateKeyLocation;
    @Value("${app.security.portal-jwt.public-key-location:}")  private String publicKeyLocation;

    @Bean
    KeyPair portalKeyPair() throws Exception {
        if (!privateKeyLocation.isBlank() && !publicKeyLocation.isBlank()) {
            log.info("Portal JWT: loading RSA key pair from files {} / {}", privateKeyLocation, publicKeyLocation);
            String priv = java.nio.file.Files.readString(java.nio.file.Path.of(privateKeyLocation));
            String pub  = java.nio.file.Files.readString(java.nio.file.Path.of(publicKeyLocation));
            return parsePemKeyPair(priv, pub);
        }

        if (!privateKeyPem.isBlank() && !publicKeyPem.isBlank()) {
            log.info("Portal JWT: loading RSA key pair from configuration");
            return parsePemKeyPair(privateKeyPem, publicKeyPem);
        }

        // No configured keys — generate an ephemeral pair.
        // We only suppress the warning for dev/test (by checking app.security.mode is not oidc,
        // but we don't have that here). Log a WARN so operators in uat/prod notice immediately.
        log.warn("Portal JWT: no RSA key configured — generating ephemeral 2048-bit key pair. " +
                 "Tokens will be invalidated on restart. " +
                 "Set app.security.portal-jwt.private-key / public-key for persistence.");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    @Bean
    JwtEncoder portalJwtEncoder(KeyPair portalKeyPair) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) portalKeyPair.getPublic())
            .privateKey((RSAPrivateKey) portalKeyPair.getPrivate())
            .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    JwtDecoder portalJwtDecoder(KeyPair portalKeyPair) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withPublicKey((RSAPublicKey) portalKeyPair.getPublic())
            .build();

        OAuth2TokenValidator<Jwt> issuerValidator  = new JwtIssuerValidator(AppConstants.Issuer.PORTAL);
        OAuth2TokenValidator<Jwt> defaultValidator  = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> combined          =
            new DelegatingOAuth2TokenValidator<>(List.of(defaultValidator, issuerValidator));
        decoder.setJwtValidator(combined);
        return decoder;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static KeyPair parsePemKeyPair(String privatePem, String publicPem) throws Exception {
        byte[] privBytes = Base64.getDecoder().decode(stripPemHeaders(privatePem));
        byte[] pubBytes  = Base64.getDecoder().decode(stripPemHeaders(publicPem));

        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateKey priv = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
        RSAPublicKey  pub  = (RSAPublicKey)  kf.generatePublic(new X509EncodedKeySpec(pubBytes));
        return new KeyPair(pub, priv);
    }

    private static String stripPemHeaders(String pem) {
        return pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s+", "");
    }
}
