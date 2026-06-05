package com.cimportal.auth;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    UserInfoAuthoritiesConverter authoritiesConverter,
                                    RestAuthEntryPoint authEntryPoint,
                                    RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
                                 "/v3/api-docs/**", "/dev/token").permitAll()
                .requestMatchers("/api/admin/**").hasRole("PORTAL_ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(authoritiesConverter)))
            .exceptionHandling(e -> e.authenticationEntryPoint(authEntryPoint)
                                     .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /** dev/test: in-process RSA keypair acting as a mock OIDC issuer (signs + verifies). */
    @Bean
    @Profile({"dev", "test"})
    KeyPair devKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    @Bean
    @Profile({"dev", "test"})
    JwtDecoder devJwtDecoder(KeyPair kp) {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) kp.getPublic()).build();
    }

    @Bean
    @Profile({"dev", "test"})
    JwtEncoder devJwtEncoder(KeyPair kp) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) kp.getPublic())
            .privateKey((RSAPrivateKey) kp.getPrivate()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }
}
