package com.rekordo.services.auth.oauth;

import com.rekordo.entity.OAuthHandoffEntity;
import com.rekordo.entity.UserEntity;
import com.rekordo.model.exception.OAuthFailedException;
import com.rekordo.repository.OAuthHandoffRepository;
import com.rekordo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Handing a finished external sign-in from the system browser back into the native app.
 *
 * <p>The browser is a different process with a different cookie jar, so the web flow's
 * refresh cookie is unreachable from the app. What crosses the gap instead is a one-time
 * code in the deep link — a value that is useless to anyone who sees it, because redeeming
 * it takes a second call the app makes itself over its own TLS connection. The refresh
 * token is minted at that point and never travels through a URL.
 *
 * <p>Only the hash is stored, exactly as for password resets: an unredeemed handoff sitting
 * in a leaked database must not be a session waiting to be claimed.
 */
@Service
@RequiredArgsConstructor
public class OAuthHandoffService {

    /**
     * Long enough for the browser to close and the app to come back to the foreground,
     * short enough that a code left in a history somewhere is dead by the time it is found.
     */
    private static final Duration LIFETIME = Duration.ofMinutes(2);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OAuthHandoffRepository handoffRepository;
    private final UserRepository userRepository;

    /** Returns the raw code to put in the deep link. */
    @Transactional
    public String issue(UserEntity user) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        OAuthHandoffEntity handoff = new OAuthHandoffEntity();
        handoff.setCodeHash(hash(code));
        handoff.setUserId(user.getId());
        handoff.setExpiresAt(Instant.now().plus(LIFETIME));
        handoff.setCreatedAt(Instant.now());
        handoffRepository.save(handoff);

        return code;
    }

    /** Redeems the code exactly once and returns whoever signed in. */
    @Transactional
    public UserEntity redeem(String code) {
        OAuthHandoffEntity handoff = handoffRepository
                .findById(hash(code == null ? "" : code))
                .filter(candidate -> candidate.getUsedAt() == null)
                .filter(candidate -> candidate.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new OAuthFailedException("That sign-in is no longer valid."));

        handoff.setUsedAt(Instant.now());
        handoffRepository.save(handoff);

        return userRepository
                .findById(handoff.getUserId())
                .orElseThrow(() -> new OAuthFailedException("That sign-in is no longer valid."));
    }

    private static String hash(String value) {
        try {
            return Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always present", e);
        }
    }
}
