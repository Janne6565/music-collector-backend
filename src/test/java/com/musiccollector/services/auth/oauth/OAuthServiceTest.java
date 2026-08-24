package com.musiccollector.services.auth.oauth;

import com.musiccollector.configuration.OAuthProperties;
import com.musiccollector.entity.OAuthStateEntity;
import com.musiccollector.model.exception.OAuthFailedException;
import com.musiccollector.repository.OAuthStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    @Mock private OAuthStateRepository stateRepository;

    private static OAuthProperties.Provider configured() {
        return new OAuthProperties.Provider(
                "Google", "client-id", "client-secret",
                "https://accounts.example/authorize", "https://accounts.example/token",
                "https://accounts.example/userinfo", "openid email", null, null);
    }

    private static OAuthProperties.Provider unconfigured() {
        return new OAuthProperties.Provider(
                "Apple", null, null, "https://appleid.example/authorize",
                "https://appleid.example/token", null, "openid email", null, null);
    }

    private OAuthService service(Map<String, OAuthProperties.Provider> providers) {
        return new OAuthService(
                new OAuthProperties("https://music.example", providers), stateRepository);
    }

    @Test
    void listsOnlyProvidersThatCouldActuallyCompleteASignIn() {
        // The client renders a button per entry, so an unconfigured provider must be
        // absent rather than a button that fails when pressed.
        var available = service(Map.of("google", configured(), "apple", unconfigured())).available();

        assertThat(available).extracting("id").containsExactly("google");
    }

    @Test
    void refusesToStartAFlowForAnUnconfiguredProvider() {
        var service = service(Map.of("apple", unconfigured()));

        assertThatThrownBy(() -> service.authorizeUrl("apple")).isInstanceOf(OAuthFailedException.class);
    }

    @Test
    void refusesAProviderItHasNeverHeardOf() {
        var service = service(Map.of("google", configured()));

        assertThatThrownBy(() -> service.authorizeUrl("myspace")).isInstanceOf(OAuthFailedException.class);
    }

    @Test
    void buildsAnAuthorizeUrlCarryingAFreshState() {
        when(stateRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        String url = service(Map.of("google", configured())).authorizeUrl("google");

        assertThat(url).startsWith("https://accounts.example/authorize");
        assertThat(url).contains("client_id=client-id");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("state=");
        assertThat(url).contains("redirect_uri=");
    }

    @Test
    void theAuthorizeUrlIsEncodedSoItIsAValidUri() {
        // Regression: a multi-scope value contains spaces, and an unencoded query made
        // URI.create throw on the very first sign-in with any real provider.
        when(stateRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        String url = service(Map.of("google", configured())).authorizeUrl("google");

        assertThat(url).doesNotContain(" ");
        assertThat(url).contains("scope=openid%20email");
        assertThat(java.net.URI.create(url)).isNotNull();
    }

    @Test
    void theRedirectUriMatchesWhatTheProviderMustHaveRegistered() {
        assertThat(service(Map.of("google", configured())).redirectUri("google"))
                .isEqualTo("https://music.example/api/v1/auth/oauth/google/callback");
    }

    private OAuthStateEntity state(String provider, Instant expiresAt, Instant usedAt) {
        OAuthStateEntity entity = new OAuthStateEntity();
        entity.setState("s");
        entity.setProvider(provider);
        entity.setExpiresAt(expiresAt);
        entity.setUsedAt(usedAt);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    @Test
    void aStateIsAcceptedExactlyOnce() {
        // A replayed callback must not be able to mint a second session.
        when(stateRepository.findById("s"))
                .thenReturn(Optional.of(state("google", Instant.now().plusSeconds(60), Instant.now())));

        assertThatThrownBy(() -> service(Map.of("google", configured())).consumeState("google", "s"))
                .isInstanceOf(OAuthFailedException.class);
    }

    @Test
    void anExpiredStateIsRefused() {
        when(stateRepository.findById("s"))
                .thenReturn(Optional.of(state("google", Instant.now().minusSeconds(1), null)));

        assertThatThrownBy(() -> service(Map.of("google", configured())).consumeState("google", "s"))
                .isInstanceOf(OAuthFailedException.class);
    }

    @Test
    void aStateIssuedForAnotherProviderIsRefused() {
        // Otherwise a state from a provider the attacker controls could complete a flow
        // against a different one.
        when(stateRepository.findById("s"))
                .thenReturn(Optional.of(state("apple", Instant.now().plusSeconds(60), null)));

        assertThatThrownBy(() -> service(Map.of("google", configured())).consumeState("google", "s"))
                .isInstanceOf(OAuthFailedException.class);
    }

    @Test
    void aMissingStateIsRefused() {
        when(stateRepository.findById("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(Map.of("google", configured())).consumeState("google", null))
                .isInstanceOf(OAuthFailedException.class);
    }
}
