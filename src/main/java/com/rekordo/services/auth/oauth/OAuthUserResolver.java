package com.rekordo.services.auth.oauth;

import com.rekordo.entity.OAuthIdentityEntity;
import com.rekordo.entity.UserEntity;
import com.rekordo.model.exception.OAuthFailedException;
import com.rekordo.repository.OAuthIdentityRepository;
import com.rekordo.repository.UserRepository;
import com.rekordo.services.auth.ConsentService;
import com.rekordo.services.mail.AccountMailEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

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

        Resolved resolved = linkOrCreate(identity);
        UserEntity user = resolved.user();

        OAuthIdentityEntity link = new OAuthIdentityEntity();
        link.setId(UUID.randomUUID());
        link.setUserId(user.getId());
        link.setProvider(provider);
        link.setProviderSubject(identity.subject());
        link.setCreatedAt(Instant.now());
        identityRepository.save(link);

        if (!resolved.created()) {
            // Only when the button was attached to an account that already existed. A new
            // account being created by the provider is not news to anybody: the person is
            // looking at the screen that did it, and there is no older way in to protect.
            events.publishEvent(new AccountMailEvent.SignInMethodLinked(user.getEmail(), provider, Instant.now()));
        }

        log.debug("Linked {} identity to user {}", provider, user.getId());
        return user;
    }

    /** Whether the account was made here decides who, if anyone, needs telling. */
    private record Resolved(UserEntity user, boolean created) {}

    private Resolved linkOrCreate(OAuthService.ExternalIdentity identity) {
        if (identity.email() != null) {
            // Linking on a verified e-mail is what lets someone who signed up with a
            // password later use the button without ending up with two collections.
            var byEmail = userRepository.findByEmailIgnoreCase(identity.email());
            if (byEmail.isPresent()) {
                return new Resolved(byEmail.get(), false);
            }
        }

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        // Providers that withhold an e-mail still get an account; the placeholder is
        // unique and never used to send anything.
        user.setEmail(identity.email() == null ? identity.subject() + "@no-email.invalid" : identity.email());
        // Confirmed by the provider, which is the same trust this class already places in a
        // provider address when it links one to an account that exists. A withheld address
        // gets a placeholder instead, and a placeholder has confirmed nothing.
        user.setEmailVerifiedAt(identity.email() == null ? null : Instant.now());
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
        return new Resolved(saved, true);
    }
}
