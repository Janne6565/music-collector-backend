package com.rekordo.services.auth.oauth;

import com.rekordo.entity.UserEntity;
import com.rekordo.model.exception.OAuthFailedException;
import com.rekordo.repository.OAuthIdentityRepository;
import com.rekordo.repository.UserRepository;
import com.rekordo.services.auth.ConsentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What an address a provider hands over is allowed to reach.
 *
 * <p>Keying on the provider's subject is what makes an existing link safe. The dangerous
 * moment is the first one, where an address is used to find an account that already exists:
 * a provider that has not proved the address is only repeating something somebody typed into
 * it, and that somebody may be anybody.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuthUserResolverTest {

    private static final String ADDRESS = "collector@example.com";

    @Mock private UserRepository userRepository;
    @Mock private OAuthIdentityRepository identityRepository;
    @Mock private ConsentService consentService;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks private OAuthUserResolver resolver;

    private static OAuthService.ExternalIdentity identity(boolean emailVerified) {
        return new OAuthService.ExternalIdentity("subject-1", ADDRESS, emailVerified, "A Collector");
    }

    private static UserEntity existing() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(ADDRESS);
        user.setEmailVerifiedAt(Instant.now());
        return user;
    }

    @Test
    void willNotLinkAnUnverifiedAddressToAnAccountThatAlreadyHoldsIt() {
        when(identityRepository.findByProviderAndProviderSubject(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(ADDRESS)).thenReturn(Optional.of(existing()));

        assertThatThrownBy(() -> resolver.resolve("google", identity(false)))
                .isInstanceOf(OAuthFailedException.class);

        verify(identityRepository, never()).save(any());
    }

    @Test
    void linksAVerifiedAddressToTheAccountThatAlreadyHoldsIt() {
        // The whole point of linking: somebody who signed up with a password and later
        // presses the button must not end up with a second, empty collection.
        UserEntity user = existing();
        when(identityRepository.findByProviderAndProviderSubject(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(ADDRESS)).thenReturn(Optional.of(user));

        assertThat(resolver.resolve("google", identity(true))).isSameAs(user);
        verify(identityRepository).save(any());
    }

    @Test
    void aNewAccountFromAnUnverifiedAddressIsNotMarkedConfirmed() {
        // Nobody else holds the address, so there is nothing to take over — but the provider
        // still has not proved it, and stamping it confirmed would hand this account a
        // password reset to a mailbox that was never shown to be readable.
        when(identityRepository.findByProviderAndProviderSubject(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(ADDRESS)).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(resolver.resolve("google", identity(false)).getEmailVerifiedAt()).isNull();
    }

    @Test
    void aNewAccountFromAVerifiedAddressIsConfirmedOnTheSpot() {
        when(identityRepository.findByProviderAndProviderSubject(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(ADDRESS)).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(resolver.resolve("google", identity(true)).getEmailVerifiedAt()).isNotNull();
    }
}
