package com.musiccollector.services.auth;

import com.musiccollector.entity.EmailVerificationEntity;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.exception.InvalidVerificationTokenException;
import com.musiccollector.repository.EmailVerificationRepository;
import com.musiccollector.repository.UserRepository;
import com.musiccollector.services.mail.AccountMailEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Confirming that the address on an account is a mailbox its owner can read.
 *
 * <p>Nothing is gated on the answer. The app is local-first and an account only adds sync, so
 * withholding sync until a link is clicked would punish the collection for something the
 * mailbox did. What confirmation buys is that the two mails that matter — a password reset
 * and a security notice — go somewhere the owner will actually see them.
 *
 * <p>Confirming is idempotent by omission rather than by error: an account that is already
 * confirmed keeps its original {@code emailVerifiedAt}, so a link clicked twice does not
 * quietly restamp the date the address was proved.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    /** Longer than a reset: nothing is locked behind it, so there is no hurry. */
    private static final Duration LIFETIME = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final EmailVerificationRepository verificationRepository;
    private final ApplicationEventPublisher events;

    /**
     * Issues a link, unless the address is already confirmed or is not an address at all.
     *
     * <p>Callable from the registration path and from the account screen, and silent in the
     * cases where there is nothing to confirm — a caller should not have to know which of
     * those it is in.
     */
    @Transactional
    public void request(UserEntity user) {
        if (user.getEmailVerifiedAt() != null || user.getEmail().endsWith("@no-email.invalid")) {
            return;
        }

        String token = OneTimeToken.issue();
        EmailVerificationEntity verification = new EmailVerificationEntity();
        verification.setId(UUID.randomUUID());
        verification.setUserId(user.getId());
        verification.setTokenHash(OneTimeToken.hash(token));
        verification.setExpiresAt(Instant.now().plus(LIFETIME));
        verification.setCreatedAt(Instant.now());
        verificationRepository.save(verification);

        events.publishEvent(new AccountMailEvent.EmailConfirmationRequested(user.getEmail(), token));
        log.debug("Confirmation link issued for user {}", user.getId());
    }

    /** Returns the account whose address was confirmed, so the caller can hand it back. */
    @Transactional
    public UserEntity confirm(String token) {
        EmailVerificationEntity verification = verificationRepository
                .findByTokenHash(OneTimeToken.hash(token))
                .filter(candidate -> candidate.getUsedAt() == null)
                .filter(candidate -> candidate.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(InvalidVerificationTokenException::new);

        UserEntity user = userRepository
                .findById(verification.getUserId())
                .orElseThrow(InvalidVerificationTokenException::new);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
        }

        verification.setUsedAt(Instant.now());
        verificationRepository.save(verification);

        log.debug("Address confirmed for user {}", user.getId());
        return user;
    }
}
