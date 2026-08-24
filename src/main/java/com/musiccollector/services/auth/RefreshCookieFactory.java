package com.musiccollector.services.auth;

import com.musiccollector.configuration.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the refresh cookie.
 *
 * httpOnly so script cannot read it, SameSite=Strict because the API is same-origin with
 * the web app (Traefik path-routes /api on the same host), and scoped to the refresh
 * endpoint so it is not attached to every request.
 */
@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "mc_refresh";
    private static final String PATH = "/api/v1/auth";

    private final Duration maxAge;
    private final boolean secure;

    public RefreshCookieFactory(
            JwtProperties properties,
            @Value("${music-collector.auth.cookie-secure:true}") boolean secure) {
        this.maxAge = properties.refreshTokenTtl();
        this.secure = secure;
    }

    public ResponseCookie create(String refreshToken) {
        return base(refreshToken).maxAge(maxAge).build();
    }

    /** An expired cookie of the same name and path, which is how a cookie is removed. */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
