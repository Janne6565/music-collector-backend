package com.musiccollector.services.auth.oauth;

import com.musiccollector.configuration.OAuthProperties;
import com.musiccollector.entity.OAuthStateEntity;
import com.musiccollector.model.core.AuthProviderDto;
import com.musiccollector.model.exception.OAuthFailedException;
import com.musiccollector.repository.OAuthStateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * The authorization-code half of external sign-in.
 *
 * Hand-rolled rather than spring-security-oauth2-client, following the house pattern: the
 * provider only authenticates the first step, and the app issues its own tokens afterwards.
 */
@Service
@RequiredArgsConstructor
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);
    private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OAuthProperties properties;
    private final OAuthStateRepository stateRepository;
    private final RestClient restClient = RestClient.builder().build();

    /** Only providers that could actually complete a sign-in. */
    public List<AuthProviderDto> available() {
        return properties.safeProviders().entrySet().stream()
                .filter(entry -> entry.getValue().configured())
                .map(entry -> new AuthProviderDto(
                        entry.getKey(),
                        entry.getValue().displayName() == null ? entry.getKey() : entry.getValue().displayName()))
                .toList();
    }

    public OAuthProperties.Provider require(String providerId) {
        OAuthProperties.Provider provider = properties.safeProviders().get(providerId);
        if (provider == null || !provider.configured()) {
            throw new OAuthFailedException("That sign-in provider is not available.");
        }
        return provider;
    }

    @Transactional
    public String authorizeUrl(String providerId) {
        OAuthProperties.Provider provider = require(providerId);

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        OAuthStateEntity entity = new OAuthStateEntity();
        entity.setState(state);
        entity.setProvider(providerId);
        entity.setExpiresAt(Instant.now().plus(STATE_LIFETIME));
        entity.setCreatedAt(Instant.now());
        stateRepository.save(entity);

        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(provider.authorizeUrl())
                .queryParam("client_id", provider.clientId())
                .queryParam("redirect_uri", redirectUri(providerId))
                .queryParam("response_type", "code")
                .queryParam("scope", provider.scope() == null ? "openid email profile" : provider.scope())
                .queryParam("state", state);

        // Apple rejects a request for name or e-mail scope that does not ask for form_post,
        // and then answers by POSTing the callback rather than redirecting to it.
        if (provider.responseMode() != null && !provider.responseMode().isBlank()) {
            url = url.queryParam("response_mode", provider.responseMode());
        }

        return url.build()
                // Encoded, or the space in a multi-scope value ("openid email profile")
                // makes an invalid URI and the redirect throws before it is ever sent.
                .encode()
                .toUriString();
    }

    /** Consumes the state exactly once; a replayed callback finds it already used. */
    @Transactional
    public void consumeState(String providerId, String state) {
        OAuthStateEntity entity = stateRepository
                .findById(state == null ? "" : state)
                .filter(candidate -> candidate.getUsedAt() == null)
                .filter(candidate -> candidate.getExpiresAt().isAfter(Instant.now()))
                .filter(candidate -> candidate.getProvider().equals(providerId))
                .orElseThrow(() -> new OAuthFailedException("That sign-in attempt is no longer valid."));
        entity.setUsedAt(Instant.now());
        stateRepository.save(entity);
    }

    /** Exchanges the code for the provider's view of who just signed in. */
    public ExternalIdentity exchange(String providerId, String code) {
        OAuthProperties.Provider provider = require(providerId);
        String secret = "apple".equals(providerId) ? AppleClientSecret.create(provider) : provider.clientSecret();

        Map<?, ?> token;
        try {
            token = restClient
                    .post()
                    .uri(provider.tokenUrl())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("grant_type=authorization_code&code=%s&redirect_uri=%s&client_id=%s&client_secret=%s"
                            .formatted(
                                    enc(code), enc(redirectUri(providerId)), enc(provider.clientId()), enc(secret)))
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException e) {
            log.warn("Token exchange with {} failed", providerId, e);
            throw new OAuthFailedException("Could not complete sign-in with that provider.");
        }
        if (token == null) {
            throw new OAuthFailedException("Could not complete sign-in with that provider.");
        }

        // The id token carries the subject and e-mail for any OIDC provider, and is the
        // only thing Apple returns — it has no userinfo endpoint worth calling.
        Object idToken = token.get("id_token");
        if (idToken instanceof String jwt) {
            ExternalIdentity fromIdToken = IdTokenClaims.read(jwt);
            if (fromIdToken != null) {
                return fromIdToken;
            }
        }

        // Fall back to userinfo for a provider that returned an opaque access token.
        Object accessToken = token.get("access_token");
        if (provider.userInfoUrl() == null || !(accessToken instanceof String bearer)) {
            throw new OAuthFailedException("That provider did not identify the account.");
        }
        Map<?, ?> info = restClient
                .get()
                .uri(provider.userInfoUrl())
                .header("Authorization", "Bearer " + bearer)
                .retrieve()
                .body(Map.class);
        if (info == null || info.get("sub") == null) {
            throw new OAuthFailedException("That provider did not identify the account.");
        }
        return new ExternalIdentity(
                String.valueOf(info.get("sub")),
                info.get("email") == null ? null : String.valueOf(info.get("email")),
                info.get("name") == null ? null : String.valueOf(info.get("name")));
    }

    /**
     * Adds the name Apple sent in the callback form, if the id token had none.
     *
     * <p>The id token always wins: it came from the provider directly, while this value
     * passed through the browser.
     */
    public ExternalIdentity named(ExternalIdentity identity, String appleUserJson) {
        if (identity.displayName() != null && !identity.displayName().isBlank()) {
            return identity;
        }
        String name = AppleUserPayload.displayName(appleUserJson);
        return name == null ? identity : new ExternalIdentity(identity.subject(), identity.email(), name);
    }

    public String redirectUri(String providerId) {
        String base = properties.publicBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                + "/api/v1/auth/oauth/" + providerId + "/callback";
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** @param subject the provider's stable id — the only field safe to key an account on */
    public record ExternalIdentity(String subject, String email, String displayName) {}
}
