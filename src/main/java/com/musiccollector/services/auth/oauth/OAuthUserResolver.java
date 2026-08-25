package com.musiccollector.services.auth.oauth;

import com.musiccollector.entity.OAuthIdentityEntity;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.exception.OAuthFailedException;
import com.musiccollector.repository.OAuthIdentityRepository;
import com.musiccollector.repository.UserRepository;
import com.musiccollector.services.auth.ConsentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Turns a provider identity into a local account.
 *
 * <p>A separate bean rather than a method on {@link OAuthService}: this must run in its own
 * transaction, and a self-invoked {@code @Transactional} call would silently not have one.
 */
@Service
@RequiredArgsConstructor
public class OAuthUserResolver {

    private static final Logger log = LoggerFactory.getLogger(OAuthUserResolver.class);

    private final UserRepository userRepository;
    private final OAuthIdentityRepository identityRepository;
    private final ConsentService consentService;

    @Transactional
    public UserEntity resolve(String provider, OAuthService.ExternalIdentity identity) {
        // Keyed on the provider's subject, never the e-mail: people change addresses, and
        // some providers hand out per-app relay addresses that change on their own.
        var existing = identityRepository.findByProviderAndProviderSubject(provider, identity.subject());
        if (existing.isPresent()) {
            return userRepository
                    .findById(existing.get().getUserId())
                    .orElseThrow(() -> new OAuthFailedException("That account no longer exists."));
        }

        UserEntity user = linkOrCreate(identity);

        OAuthIdentityEntity link = new OAuthIdentityEntity();
        link.setId(UUID.randomUUID());
        link.setUserId(user.getId());
        link.setProvider(provider);
        link.setProviderSubject(identity.subject());
        link.setCreatedAt(Instant.now());
        identityRepository.save(link);

        log.debug("Linked {} identity to user {}", provider, user.getId());
        return user;
    }

    private UserEntity linkOrCreate(OAuthService.ExternalIdentity identity) {
        if (identity.email() != null) {
            // Linking on a verified e-mail is what lets someone who signed up with a
            // password later use the button without ending up with two collections.
            var byEmail = userRepository.findByEmailIgnoreCase(identity.email());
            if (byEmail.isPresent()) {
                return byEmail.get();
            }
        }

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        // Providers that withhold an e-mail still get an account; the placeholder is
        // unique and never used to send anything.
        user.setEmail(identity.email() == null ? identity.subject() + "@no-email.invalid" : identity.email());
        // No password: this account can only be reached through the provider until
        // somebody sets one.
        user.setPasswordHash(null);
        user.setDisplayName(identity.displayName());
        user.setTokenVersion(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        UserEntity saved = userRepository.save(user);
        // The provider buttons carry the legal notice instead of two tick boxes -- there is
        // no form to put them in, and a screen that demands ticks before it will let you
        // press "Continue with Apple" is a screen nobody finishes. The record is the same
        // one a password sign-up leaves, because the agreement is the same.
        consentService.recordSignUp(saved.getId());
        return saved;
    }
}
