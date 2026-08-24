package com.musiccollector.services.auth;

import com.musiccollector.configuration.MailProperties;
import com.musiccollector.entity.PasswordResetEntity;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.exception.InvalidResetTokenException;
import com.musiccollector.repository.PasswordResetRepository;
import com.musiccollector.repository.UserRepository;
import com.musiccollector.services.mail.MailPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
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

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailPort mailPort;
    private final MailProperties mailProperties;

    @Transactional
    public void request(String email) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            log.debug("Reset requested for an address with no account");
            return;
        }

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        PasswordResetEntity reset = new PasswordResetEntity();
        reset.setId(UUID.randomUUID());
        reset.setUserId(user.getId());
        reset.setTokenHash(hash(token));
        reset.setExpiresAt(Instant.now().plus(LIFETIME));
        reset.setCreatedAt(Instant.now());
        passwordResetRepository.save(reset);

        String link = "%s/reset?token=%s".formatted(trimTrailingSlash(mailProperties.publicUrl()), token);
        mailPort.send(
                user.getEmail(),
                "Reset your Music Collector password",
                """
                <p>Someone asked to reset the password for your Music Collector account.</p>
                <p><a href="%s">Choose a new password</a></p>
                <p>The link works once and expires in an hour. If this wasn't you, nothing has
                changed and you can ignore this message.</p>
                """.formatted(link),
                """
                Someone asked to reset the password for your Music Collector account.

                Choose a new password: %s

                The link works once and expires in an hour. If this wasn't you, nothing has
                changed and you can ignore this message.
                """.formatted(link));

        log.debug("Reset link issued for user {}", user.getId());
    }

    /** Returns the user whose password was changed, so the caller can sign them straight in. */
    @Transactional
    public UserEntity redeem(String token, String newPassword) {
        PasswordResetEntity reset = passwordResetRepository
                .findByTokenHash(hash(token))
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

        log.debug("Password reset completed for user {}", user.getId());
        return user;
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the platform", e);
        }
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
