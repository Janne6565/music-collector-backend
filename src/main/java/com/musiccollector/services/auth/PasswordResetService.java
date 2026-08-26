package com.musiccollector.services.auth;

import com.musiccollector.entity.PasswordResetEntity;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.exception.InvalidResetTokenException;
import com.musiccollector.repository.PasswordResetRepository;
import com.musiccollector.repository.UserRepository;
import com.musiccollector.services.mail.AccountMailEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Forgotten-password reset.
 *
 * Three properties matter more than the happy path:
 *
 * <ul>
 *   <li><b>No account enumeration.</b> Requesting a reset answers the same way whether or
 *       not the address is registered, so this endpoint cannot be used to find out who has
 *       an account.
 *   <li><b>Only a hash is stored.</b> The raw token exists in the e-mail and nowhere else,
 *       so a database leak is not a pile of account takeovers.
 *   <li><b>Redeeming revokes every session.</b> Someone resetting a password may be doing
 *       it because another party has access; leaving that party signed in would defeat it.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** Long enough to find the mail, short enough that an unread one stops working. */
    private static final Duration LIFETIME = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    @Transactional
    public void request(String email) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            log.debug("Reset requested for an address with no account");
            return;
        }
        // An unconfirmed address is one nobody has shown they can read, and mailing a
        // password reset to it would hand the account to whoever is actually there. This is
        // the first of the three things being unconfirmed costs (design 21f), and the
        // sign-in screen says so up front rather than leaving it to be discovered here --
        // silence is required at this endpoint, so it cannot be the place that explains.
        //
        // Accounts that predate confirmation were stamped by V27 and are unaffected.
        if (user.getEmailVerifiedAt() == null) {
            log.debug("Reset requested for an unconfirmed address on user {}", user.getId());
            return;
        }

        String token = OneTimeToken.issue();

        PasswordResetEntity reset = new PasswordResetEntity();
        reset.setId(UUID.randomUUID());
        reset.setUserId(user.getId());
        reset.setTokenHash(OneTimeToken.hash(token));
        reset.setExpiresAt(Instant.now().plus(LIFETIME));
        reset.setCreatedAt(Instant.now());
        passwordResetRepository.save(reset);

        events.publishEvent(new AccountMailEvent.PasswordResetRequested(user.getEmail(), token));

        log.debug("Reset link issued for user {}", user.getId());
    }

    /** Returns the user whose password was changed, so the caller can sign them straight in. */
    @Transactional
    public UserEntity redeem(String token, String newPassword) {
        PasswordResetEntity reset = passwordResetRepository
                .findByTokenHash(OneTimeToken.hash(token))
                .filter(candidate -> candidate.getUsedAt() == null)
                .filter(candidate -> candidate.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(InvalidResetTokenException::new);

        UserEntity user = userRepository.findById(reset.getUserId()).orElseThrow(InvalidResetTokenException::new);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Every outstanding session dies with the old password. Someone resetting may be
        // locking another party out, and leaving that party signed in would defeat it.
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        reset.setUsedAt(Instant.now());
        passwordResetRepository.save(reset);

        // A notice, not a question: someone whose password was changed without them asking
        // has no other way of finding out, and the mail is the only thing standing between a
        // quiet takeover and being noticed.
        events.publishEvent(new AccountMailEvent.PasswordChanged(user.getEmail(), Instant.now()));

        log.debug("Password reset completed for user {}", user.getId());
        return user;
    }
}
