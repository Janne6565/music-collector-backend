package com.musiccollector.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * Endpoints reachable without an account. The metadata proxy is deliberately open:
     * the app is local-first and anonymous users must be able to search releases and scan
     * barcodes. Abuse is bounded by the per-IP rate limiter and the response cache in front
     * of MusicBrainz, not by authentication.
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/health",
            "/api/v1/metadata/**",
            "/api/v1/auth/**",
            "/actuator/health/**",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Stateless JWT API: there is no session and no browser-submitted form, so
                // there is no CSRF vector to protect. The refresh cookie is SameSite=Strict.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
