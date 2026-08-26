package com.musiccollector.services.auth;

import com.musiccollector.entity.EmailVerificationEntity;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.core.EmailConfirmationDto;
import com.musiccollector.model.exception.EmailAlreadyRegisteredException;
import com.musiccollector.model.exception.InvalidCredentialsException;
import com.musiccollector.model.exception.InvalidVerificationTokenException;
import com.musiccollector.repository.EmailVerificationRepository;
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
import java.util.List;
import java.util.UUID;

/**
 * Confirming that the address on an account is a mailbox its owner can read, and moving the
 * account to a different one.
 *
 * <p><b>Nothing in the app is gated on the answer</b> (design 21f). The collection is
 * local-first and an account only adds sync; holding sync back until a mailbox answers
 * punishes the records for the mailbox, and it lands hardest on the person whose mail went
 * to spam. What being unconfirmed costs is confined to the mailbox: no password reset
 * ({@link PasswordResetService}), and nothing that would arrive by mail has anywhere to go.
 * No record, photo, wish or friend is ever affected, which is the test any future gate has
 * to pass.
 *
 * <p>Two rules shape the rest:
 *
 * <ul>
 *   <li><b>Only one link is ever live.</b> Issuing a new one retires whatever was
 *       outstanding, so the older mail is never the one that works — and inside the first
 *       minute nothing is issued at all, because pressing the button twice is impatience
 *       rather than a mistake and a second mail lands in the same place as the first.
 *   <li><b>A change never costs the recovery you already had.</b> The old address keeps
 *       working until the new one answers, so a typo cannot lock anybody out. The account
 *       is not un-confirmed meanwhile; it is confirmed at the old address and pending at
 *       the new.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    /** Longer than a reset: nothing is locked behind it, so there is no hurry. */
    private static final Duration LIFETIME = Duration.ofHours(24);

    /** Long enough that the second press is answered by a countdown instead of a duplicate. */
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    /**
     * How long the old mailbox can still undo a change after it has gone through. It
     * deliberately outlives the change: an undo that expired with the link would only have
     * to be waited out by whoever was at the keyboard.
     */
    private static final Duration CANCEL_WINDOW = Duration.ofHours(24);

    static final String PLACEHOLDER_DOMAIN = "@no-email.invalid";

    private final UserRepository userRepository;
    private final EmailVerificationRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    /** What the account row draws, and the only way it survives a reload. */
    @Transactional(readOnly = true)
    public EmailConfirmationDto status(UserEntity user) {
        return describe(user, outstanding(user.getId()).stream().findFirst().orElse(null));
    }

    /**
     * Issues a confirmation link for the address already on the account.
     *
     * <p>Silent in every case where there is nothing to do — already confirmed, no real
     * address, or a link sent moments ago — because a caller should not have to know which
     * of those it is in, and none of them is an error.
     */
    @Transactional
    public EmailConfirmationDto request(UserEntity user) {
        if (user.getEmailVerifiedAt() != null || user.getEmail().endsWith(PLACEHOLDER_DOMAIN)) {
            return status(user);
        }

        List<EmailVerificationEntity> live = outstanding(user.getId());
        EmailVerificationEntity newest = live.stream().findFirst().orElse(null);
        if (newest != null && Instant.now().isBefore(newest.getCreatedAt().plus(RESEND_COOLDOWN))) {
            // The first link is still the valid one. Saying so with a countdown beats both a
            // duplicate mail and an error about a button somebody was right to press.
            return describe(user, newest);
        }

        live.forEach(this::retire);
        String token = OneTimeToken.issue();
        EmailVerificationEntity issued = open(user.getId(), token);
        verificationRepository.save(issued);

        events.publishEvent(new AccountMailEvent.EmailConfirmationRequested(user.getEmail(), token));
        log.debug("Confirmation link issued for user {}", user.getId());
        return describe(user, issued);
    }

    /**
     * The same link, asked for by somebody with no session — the dead-link state on 21d.
     *
     * <p>Silent in every case, including an address with no account and one already
     * confirmed: answering differently would turn this into a way to find out who is
     * registered, exactly as it would at the forgotten-password endpoint.
     */
    @Transactional
    public void requestFor(String email) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            log.debug("Confirmation requested for an address with no account");
            return;
        }
        request(user);
    }

    /**
     * Starts a move to a different address.
     *
     * <p>Nothing about the account changes here: the link goes out, the old mailbox is told,
     * and the account carries on answering to the address it always did.
     */
    @Transactional
    public EmailConfirmationDto requestChange(UserEntity user, String requestedEmail, String password) {
        String target = requestedEmail.trim();
        if (target.equalsIgnoreCase(user.getEmail())) {
            // Not an error worth a message: the account already is where it was asked to go.
            return status(user);
        }
        if (userRepository.existsByEmailIgnoreCase(target)) {
            throw new EmailAlreadyRegisteredException();
        }
        // An account made through a provider has no password to ask for. Refusing the change
        // would strand it at an address it never chose, so the field is skipped rather than
        // faked -- and the notice to the old address is what covers the gap.
        if (user.getPasswordHash() != null
                && (password == null || !passwordEncoder.matches(password, user.getPasswordHash()))) {
            throw new InvalidCredentialsException();
        }

        outstanding(user.getId()).forEach(this::retire);

        String token = OneTimeToken.issue();
        EmailVerificationEntity change = open(user.getId(), token);
        change.setNewEmail(target);
        change.setPreviousEmail(user.getEmail());

        // Only an address that was ever proved gets an undo. Changing away from an
        // unconfirmed one is the common case -- a typo -- and there is nobody at the old
        // mailbox to defend, because nothing was ever shown to be there.
        if (user.getEmailVerifiedAt() != null) {
            String cancelToken = OneTimeToken.issue();
            change.setCancelTokenHash(OneTimeToken.hash(cancelToken));
            change.setCancelExpiresAt(Instant.now().plus(LIFETIME).plus(CANCEL_WINDOW));
            events.publishEvent(new AccountMailEvent.EmailChangeStarted(
                    user.getEmail(), target, cancelToken, Instant.now()));
        }
        verificationRepository.save(change);

        events.publishEvent(new AccountMailEvent.EmailChangeRequested(target, token));
        log.debug("Address change started for user {}", user.getId());
        return describe(user, change);
    }

    /**
     * Redeems a link — either confirming the address on file or completing a change.
     *
     * <p>Confirming is idempotent by omission: an account already confirmed keeps its
     * original stamp, because when the address was proved is a fact and a link clicked twice
     * is not a second proof.
     */
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

        if (verification.getNewEmail() != null) {
            // The address could have been claimed by somebody else while the link sat in a
            // mailbox, so it is checked again here rather than only when the change started.
            if (userRepository.existsByEmailIgnoreCase(verification.getNewEmail())) {
                throw new EmailAlreadyRegisteredException();
            }
            user.setEmail(verification.getNewEmail());
        }
        if (user.getEmailVerifiedAt() == null || verification.getNewEmail() != null) {
            user.setEmailVerifiedAt(Instant.now());
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        verification.setUsedAt(Instant.now());
        verificationRepository.save(verification);

        log.debug("Address confirmed for user {}", user.getId());
        return user;
    }

    /**
     * Undoes a change from the link in the notice, whether or not it has gone through yet.
     *
     * <p>Open to anybody holding the token, because the point of it is to work from a
     * mailbox that may no longer be able to sign in.
     */
    @Transactional
    public void cancelChange(String cancelToken) {
        EmailVerificationEntity change = verificationRepository
                .findByCancelTokenHash(OneTimeToken.hash(cancelToken))
                .filter(candidate -> candidate.getCancelExpiresAt() != null)
                .filter(candidate -> candidate.getCancelExpiresAt().isAfter(Instant.now()))
                .orElseThrow(InvalidVerificationTokenException::new);

        UserEntity user = userRepository.findById(change.getUserId()).orElseThrow(InvalidVerificationTokenException::new);
        if (change.getUsedAt() != null && change.getPreviousEmail() != null) {
            // Already gone through. Putting the address back is the whole reason this token
            // outlives the link.
            user.setEmail(change.getPreviousEmail());
            user.setUpdatedAt(Instant.now());
            // Whoever asked for the change is holding a session; a hijack undone that leaves
            // them signed in has been undone in name only.
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
        }
        change.setUsedAt(Instant.now());
        change.setCancelTokenHash(null);
        change.setCancelExpiresAt(null);
        verificationRepository.save(change);

        log.info("Address change cancelled for user {}", user.getId());
    }

    /** The Cancel on the waiting row: called by somebody already signed in, before it lands. */
    @Transactional
    public EmailConfirmationDto cancelPendingChange(UserEntity user) {
        outstanding(user.getId()).stream()
                .filter(candidate -> candidate.getNewEmail() != null)
                .forEach(this::retire);
        return status(user);
    }

    private List<EmailVerificationEntity> outstanding(UUID userId) {
        Instant now = Instant.now();
        return verificationRepository.findOutstanding(userId).stream()
                .filter(candidate -> candidate.getExpiresAt().isAfter(now))
                .toList();
    }

    private EmailVerificationEntity open(UUID userId, String token) {
        EmailVerificationEntity verification = new EmailVerificationEntity();
        verification.setId(UUID.randomUUID());
        verification.setUserId(userId);
        verification.setTokenHash(OneTimeToken.hash(token));
        verification.setExpiresAt(Instant.now().plus(LIFETIME));
        verification.setCreatedAt(Instant.now());
        return verification;
    }

    /**
     * Spends a link without redeeming it, so two of them are never live at once.
     *
     * <p>The undo goes with it. A change that was called off has nothing to undo, and a
     * cancel link that still worked would sign every device out over a change that never
     * happened. Redeeming does not come through here, which is what lets the undo of a
     * change that <em>did</em> happen outlive its link.
     */
    private void retire(EmailVerificationEntity verification) {
        verification.setUsedAt(Instant.now());
        verification.setCancelTokenHash(null);
        verification.setCancelExpiresAt(null);
        verificationRepository.save(verification);
    }

    private EmailConfirmationDto describe(UserEntity user, EmailVerificationEntity outstanding) {
        if (outstanding == null) {
            return new EmailConfirmationDto(user.getEmailVerifiedAt() != null, null, null, 0, null);
        }
        long retryAfter = Math.max(
                0,
                Duration.between(Instant.now(), outstanding.getCreatedAt().plus(RESEND_COOLDOWN))
                        .toSeconds());
        return new EmailConfirmationDto(
                user.getEmailVerifiedAt() != null,
                outstanding.getCreatedAt(),
                outstanding.getExpiresAt(),
                retryAfter,
                outstanding.getNewEmail());
    }
}
