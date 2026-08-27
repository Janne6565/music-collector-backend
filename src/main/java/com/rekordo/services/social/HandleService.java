package com.rekordo.services.social;

import com.rekordo.entity.HandleChangeEntity;
import com.rekordo.entity.UserEntity;
import com.rekordo.model.core.HandleAvailabilityDto;
import com.rekordo.model.exception.HandleChangeLimitException;
import com.rekordo.model.exception.HandleUnavailableException;
import com.rekordo.repository.HandleChangeRepository;
import com.rekordo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The one public identifier an account has.
 *
 * <p>Deliberately separate from the display name: a name is what the app calls you and
 * changes freely, a handle is what other people type and link to. Keeping them apart is
 * what lets somebody be findable without their real name being searchable.
 */
@Service
@RequiredArgsConstructor
public class HandleService {

    private static final Logger log = LoggerFactory.getLogger(HandleService.class);

    /**
     * Letters, numbers and dots, as the claim screen promises. Anchored at both ends by an
     * alphanumeric so a handle cannot open or close with a dot, and no two dots may sit
     * together — both of which produce handles that read as typos of each other.
     */
    private static final Pattern SHAPE = Pattern.compile("^[a-z0-9](?:[a-z0-9]|\\.(?=[a-z0-9])){1,28}[a-z0-9]$");

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 30;

    /** "You can change it twice a year." */
    private static final int CHANGES_PER_WINDOW = 2;

    private static final Duration CHANGE_WINDOW = Duration.ofDays(365);

    /**
     * How long a handle somebody gave up stays out of circulation.
     *
     * <p>Without this, changing handle hands the old one — and every link and pending
     * request pointing at it — to whoever claims it next.
     */
    private static final Duration RELEASED_HANDLE_COOLDOWN = Duration.ofDays(180);

    /**
     * Words the app needs for itself. The public profile lives at {@code /@handle} and its
     * wishlist at {@code /@handle/wishlist}, so anything that is a path segment or looks
     * like official account has to be off the table.
     */
    private static final Set<String> RESERVED = Set.of(
            "about", "account", "admin", "api", "app", "assets", "auth", "callback", "collection",
            "copies", "help", "health", "home", "library", "login", "logout", "me", "metadata",
            "new", "oauth", "photos", "privacy", "profile", "public", "register", "reset",
            "root", "settings", "sharing", "signin", "signup", "static", "support", "sync",
            "terms", "us", "wishlist", "you");

    private final UserRepository userRepository;
    private final HandleChangeRepository handleChangeRepository;

    /** Whether a handle could be claimed by this account right now, and if not, why. */
    @Transactional(readOnly = true)
    public HandleAvailabilityDto check(UUID userId, String raw) {
        String handle = normalise(raw);
        HandleAvailabilityDto.Reason problem = problemWith(userId, handle);
        return problem == null ? HandleAvailabilityDto.ok(handle) : HandleAvailabilityDto.no(handle, problem);
    }

    @Transactional
    public UserEntity claim(UUID userId, String raw) {
        String handle = normalise(raw);
        UserEntity user = userRepository.findById(userId).orElseThrow();

        if (handle.equals(normalise(user.getHandle()))) {
            // Saving the handle you already have is not a change, so it must not spend one
            // of the two the year allows.
            return user;
        }

        HandleAvailabilityDto.Reason problem = problemWith(userId, handle);
        if (problem != null) {
            throw new HandleUnavailableException(handle, problem);
        }
        if (user.getHandle() != null && changesRemaining(userId) <= 0) {
            throw new HandleChangeLimitException(CHANGES_PER_WINDOW);
        }

        user.setHandle(handle);
        user.setUpdatedAt(Instant.now());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two people claiming the same free handle in the same instant. The unique index
            // is the real arbiter; the check above is only there to give a good answer first.
            throw new HandleUnavailableException(handle, HandleAvailabilityDto.Reason.TAKEN);
        }

        HandleChangeEntity change = new HandleChangeEntity();
        change.setId(UUID.randomUUID());
        change.setUserId(userId);
        change.setHandle(handle);
        change.setChangedAt(Instant.now());
        handleChangeRepository.save(change);

        log.debug("User {} claimed handle @{}", userId, handle);
        return user;
    }

    /** How many more times this account may change handle before the window rolls off. */
    @Transactional(readOnly = true)
    public int changesRemaining(UUID userId) {
        long used = handleChangeRepository.countByUserIdAndChangedAtAfter(
                userId, Instant.now().minus(CHANGE_WINDOW));
        // The first claim is recorded too but is not a change, so one is free.
        long changes = Math.max(0, used - 1);
        return (int) Math.max(0, CHANGES_PER_WINDOW - changes);
    }

    private HandleAvailabilityDto.Reason problemWith(UUID userId, String handle) {
        if (handle.length() < MIN_LENGTH || handle.length() > MAX_LENGTH || !SHAPE.matcher(handle).matches()) {
            return HandleAvailabilityDto.Reason.MALFORMED;
        }
        if (RESERVED.contains(handle)) {
            return HandleAvailabilityDto.Reason.RESERVED;
        }
        if (userRepository.existsByHandleIgnoreCase(handle)) {
            return HandleAvailabilityDto.Reason.TAKEN;
        }
        boolean recentlyReleased = !handleChangeRepository
                .findRecentClaimsByOthers(handle, userId, Instant.now().minus(RELEASED_HANDLE_COOLDOWN))
                .isEmpty();
        return recentlyReleased ? HandleAvailabilityDto.Reason.TAKEN : null;
    }

    /**
     * Handles are compared and stored lowercased. Letting @Anna and @anna both exist would
     * make one of them a convincing impersonation of the other.
     */
    private static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("@") ? trimmed.substring(1) : trimmed;
    }
}
