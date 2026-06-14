package com.cimportal.auth;

import com.cimportal.common.AppConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    UserInfoAuthoritiesConverter authoritiesConverter,
                                    MultiIssuerJwtDecoder multiIssuerJwtDecoder,
                                    RestAuthEntryPoint authEntryPoint,
                                    RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
                                 "/v3/api-docs/**", "/architecture.html", AppConstants.Paths.DEV_TOKEN,
                                 AppConstants.Paths.PORTAL_CONFIG,
                                 AppConstants.Auth.LOGIN_PATH).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/icons/**").permitAll()
                .requestMatchers(AppConstants.Paths.ADMIN_API).hasRole(AppConstants.Roles.PORTAL_ADMIN)
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                .decoder(multiIssuerJwtDecoder)
                .jwtAuthenticationConverter(authoritiesConverter)))
            .exceptionHandling(e -> e.authenticationEntryPoint(authEntryPoint)
                                     .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(props.allowedOrigins());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(false);
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}
