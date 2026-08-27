package com.rekordo.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * External sign-in providers.
 *
 * A provider exists only when its client id and secret are set. That is deliberate: the
 * sign-in screen asks the server which providers are available and renders buttons for
 * those alone, so an unconfigured provider is invisible rather than a button that fails.
 *
 * @param publicBaseUrl      the app's own origin, used to build the redirect URI the
 *                           provider must have registered
 * @param mobileRedirectUri  where the callback sends a native app once it has finished.
 *                           A custom URL scheme, so the phone reopens the app rather than
 *                           leaving the person looking at the website they just signed
 *                           into. It is never given to the provider — providers only ever
 *                           see {@code publicBaseUrl}.
 */
@ConfigurationProperties(prefix = "rekordo.oauth")
public record OAuthProperties(String publicBaseUrl, String mobileRedirectUri, Map<String, Provider> providers) {

    /**
     * @param clientSecret for Apple this is a private key in PKCS#8 (the contents of the
     *                     .p8 file), because Apple requires a signed JWT rather than a
     *                     static secret. See {@code AppleClientSecret}.
     * @param teamId       Apple only
     * @param keyId        Apple only
     * @param responseMode how the provider returns the code. Blank means the ordinary
     *                     redirect with query parameters. Apple requires {@code form_post}
     *                     whenever name or e-mail scope is asked for, and answers with a
     *                     cross-site form POST instead of a GET.
     */
    public record Provider(
            String displayName,
            String clientId,
            String clientSecret,
            String authorizeUrl,
            String tokenUrl,
            String userInfoUrl,
            String scope,
            String teamId,
            String keyId,
            String responseMode) {

        public boolean postsBack() {
            return "form_post".equalsIgnoreCase(responseMode);
        }

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public String safeMobileRedirectUri() {
        return mobileRedirectUri == null || mobileRedirectUri.isBlank()
                ? "musiccollector://auth/callback"
                : mobileRedirectUri;
    }

    public Map<String, Provider> safeProviders() {
        return providers == null ? Map.of() : providers;
    }
}
