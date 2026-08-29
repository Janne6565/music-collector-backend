package com.rekordo.services.auth.oauth;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The cookie that ties an external sign-in to the browser that began it.
 *
 * <p>{@code SameSite=None}, unlike the refresh cookie, and that is forced rather than chosen:
 * the callback arrives from the provider's origin, so a {@code Strict} cookie would not be
 * sent at all and a {@code Lax} one would be sent on Google's redirect but not on Apple's
 * cross-site form POST. {@code None} requires {@code Secure}, which is set unconditionally --
 * browsers treat {@code localhost} as trustworthy, so local development over HTTP still
 * works, but a dev backend reached at a plain-HTTP LAN address will not complete an external
 * sign-in. That is the deliberate cost of the check holding everywhere it runs.
 *
 * <p>Path-scoped to the OAuth endpoints, httpOnly, and no longer-lived than the state it
 * matches: nothing else ever needs it, and nothing may read it back out of the page.
 */
@Component
public class OAuthStateCookieFactory {

    public static final String COOKIE_NAME = "mc_oauth";
    private static final String PATH = "/api/v1/auth/oauth";

    public ResponseCookie create(String binding, Duration lifetime) {
        return base(binding).maxAge(lifetime).build();
    }

    /** An expired cookie of the same name and path, which is how a cookie is removed. */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(PATH);
    }
}
