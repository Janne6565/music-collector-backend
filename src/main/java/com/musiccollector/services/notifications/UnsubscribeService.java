package com.musiccollector.services.notifications;

import com.musiccollector.entity.UnsubscribeTokenEntity;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.core.NotificationCategory;
import com.musiccollector.model.exception.InvalidVerificationTokenException;
import com.musiccollector.repository.UnsubscribeTokenRepository;
import com.musiccollector.repository.UserRepository;
import com.musiccollector.services.auth.OneTimeToken;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The one-click way out of a category, from a mail client with no session (design 22f).
 *
 * <p>Standing rather than one-time: the same link goes in every digest and keeps working, so
 * somebody who acts on a mail from three weeks ago is not told their link has expired. Only
 * the hash is stored, the same as every other token here.
 *
 * <p><b>A token names exactly one category and cannot name another.</b> A link that also
 * silenced security notices would be a trap, and the copy in the footer says which one it
 * is. A locked category is refused outright — the switch does not exist, so neither does a
 * way to reach around it.
 */
@Service
@RequiredArgsConstructor
public class UnsubscribeService {

    private static final Logger log = LoggerFactory.getLogger(UnsubscribeService.class);

    private final UnsubscribeTokenRepository repository;
    private final UserRepository userRepository;
    private final NotificationPreferenceService preferenceService;

    /** The link for this account and category, minted once and reused ever after. */
    @Transactional
    public String tokenFor(UserEntity user, NotificationCategory category) {
        if (category.mailLocked()) {
            throw new IllegalArgumentException("A locked category has no way out");
        }
        // Reissued rather than looked up, because only the hash is kept: nothing can read an
        // existing token back, so a row that exists is replaced with one we can print.
        String token = OneTimeToken.issue();
        UnsubscribeTokenEntity row = repository
                .findByUserIdAndCategory(user.getId(), category)
                .orElseGet(() -> {
                    UnsubscribeTokenEntity fresh = new UnsubscribeTokenEntity();
                    fresh.setId(UUID.randomUUID());
                    fresh.setUserId(user.getId());
                    fresh.setCategory(category);
                    fresh.setCreatedAt(Instant.now());
                    return fresh;
                });
        row.setTokenHash(OneTimeToken.hash(token));
        repository.save(row);
        return token;
    }

    /**
     * Switches off the one category the token names, and answers which it was so the page
     * can say it out loud.
     */
    @Transactional
    public NotificationCategory redeem(String token) {
        UnsubscribeTokenEntity row = repository
                .findByTokenHash(OneTimeToken.hash(token))
                .orElseThrow(InvalidVerificationTokenException::new);
        UserEntity user = userRepository.findById(row.getUserId()).orElseThrow(InvalidVerificationTokenException::new);

        // Push is left exactly as it was. The link came out of a mail and says "stop these
        // weekly mails"; silencing a channel it never mentioned would be doing more than it
        // promised.
        boolean push = preferenceService.pushEnabled(user, row.getCategory());
        preferenceService.update(user, row.getCategory(), false, push);

        log.info("Unsubscribed user {} from {}", user.getId(), row.getCategory());
        return row.getCategory();
    }
}
