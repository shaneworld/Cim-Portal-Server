package com.cimportal.auth;

import com.cimportal.common.AppConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jwt.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fast unit test for {@link PortalJwtKeys} — no Spring context required.
 * Exercises the new {@code *-key-location} PEM-file loading path and the
 * ephemeral-key fallback.
 */
class PortalJwtKeysTest {

    @TempDir
    Path tempDir;

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Create a fresh {@link PortalJwtKeys} with all @Value fields blank. */
    private static PortalJwtKeys blank() throws Exception {
        PortalJwtKeys keys = new PortalJwtKeys();
        setField(keys, "privateKeyPem",      "");
        setField(keys, "publicKeyPem",       "");
        setField(keys, "privateKeyLocation", "");
        setField(keys, "publicKeyLocation",  "");
        return keys;
    }

    private static void setField(Object target, String name, String value) throws Exception {
        Field f = PortalJwtKeys.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static String toPkcs8Pem(byte[] encoded) {
        return "-----BEGIN PRIVATE KEY-----\n"
             + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
             + "\n-----END PRIVATE KEY-----\n";
    }

    private static String toX509Pem(byte[] encoded) {
        return "-----BEGIN PUBLIC KEY-----\n"
             + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
             + "\n-----END PUBLIC KEY-----\n";
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void keyFiles_loadsUsablePairAndRoundTripsJwt() throws Exception {
        // Generate a 2048-bit RSA key pair in the test JVM
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair generated = gen.generateKeyPair();

        // Write PEM files to the temp directory
        Path privFile = tempDir.resolve("portal-private.pem");
        Path pubFile  = tempDir.resolve("portal-public.pem");
        Files.writeString(privFile, toPkcs8Pem(generated.getPrivate().getEncoded()));
        Files.writeString(pubFile,  toX509Pem(generated.getPublic().getEncoded()));

        // Build a PortalJwtKeys with the location fields set
        PortalJwtKeys keys = blank();
        setField(keys, "privateKeyLocation", privFile.toString());
        setField(keys, "publicKeyLocation",  pubFile.toString());

        KeyPair pair = keys.portalKeyPair();
        assertThat(pair).isNotNull();
        assertThat(pair.getPrivate()).isNotNull();
        assertThat(pair.getPublic()).isNotNull();

        // Round-trip: sign a JWT then verify it
        JwtEncoder encoder = keys.portalJwtEncoder(pair);
        JwtDecoder decoder = keys.portalJwtDecoder(pair);

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(AppConstants.Issuer.PORTAL)
            .subject("TEST_EMP")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
            .build();

        String token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        assertThat(token).isNotBlank();

        Jwt decoded = decoder.decode(token);
        // getIssuer() attempts URL conversion; use raw claim to avoid that for non-URL issuers
        assertThat(decoded.getClaimAsString("iss")).isEqualTo(AppConstants.Issuer.PORTAL);
        assertThat(decoded.getSubject()).isEqualTo("TEST_EMP");
    }

    @Test
    void noLocationOrInlineKey_ephemeralPairGeneratedWithoutThrowing() throws Exception {
        PortalJwtKeys keys = blank();

        KeyPair pair = keys.portalKeyPair();
        assertThat(pair).isNotNull();
        assertThat(pair.getPrivate()).isNotNull();
        assertThat(pair.getPublic()).isNotNull();
    }
}
