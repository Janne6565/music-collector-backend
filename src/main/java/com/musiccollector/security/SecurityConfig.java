package com.musiccollector.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Endpoints reachable without an account.
     *
     * The metadata proxy is deliberately open: the app is local-first, and someone with no
     * account must still be able to search releases and scan barcodes. Abuse is bounded by
     * the per-IP rate limiter and the response cache in front of MusicBrainz, not by
     * authentication.
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/health",
            "/api/v1/metadata/**",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/actuator/health/**",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
    };

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Stateless token API: there is no session and no browser-submitted form,
                // so there is no CSRF vector. The refresh cookie is SameSite=Strict and is
                // only ever exchanged by an explicit POST from the app's own origin.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                // 401 with an empty body rather than a redirect or a WWW-Authenticate
                // challenge, which would make the browser show its own password prompt.
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
