package com.rekordo.services.auth;

import com.rekordo.entity.PhotoEntity;
import com.rekordo.entity.UserEntity;
import com.rekordo.model.action.LoginRequest;
import com.rekordo.model.action.RegisterRequest;
import com.rekordo.model.core.SessionDto;
import com.rekordo.model.core.TokenType;
import com.rekordo.model.core.UserDto;
import com.rekordo.model.exception.EmailAlreadyRegisteredException;
import com.rekordo.model.exception.InvalidCredentialsException;
import com.rekordo.model.exception.NotAuthenticatedException;
import com.rekordo.repository.CopyRepository;
import com.rekordo.repository.PhotoRepository;
import com.rekordo.repository.UserRepository;
import com.rekordo.services.mail.AccountMailEvent;
import com.rekordo.services.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * Hashed once at startup and compared against when no user matches, so a failed login
     * for an unknown address takes about as long as one for a known address. Without it,
     * response timing tells an attacker which addresses are registered.
     */
    private static final String TIMING_EQUALISER_PASSWORD = "timing-equaliser-not-a-real-password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final ConsentService consentService;
    private final CopyRepository copyRepository;
    private final EmailVerificationService emailVerificationService;
    private final ApplicationEventPublisher events;

    @Transactional
    public Session register(RegisterRequest request) {
        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        String displayName = request.displayName() == null ? null : request.displayName().trim();
        user.setDisplayName(displayName == null || displayName.isEmpty() ? null : displayName);
        user.setTokenVersion(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        // Inside the same transaction as the account itself: an account without its consent
        // rows is the one state the record is there to make impossible.
        consentService.recordSignUp(user.getId());
        // Issued here rather than left to the account screen: an address nobody ever
        // confirms is one a password reset cannot reach, and the moment somebody typed it is
        // the moment they are still looking at their inbox.
        emailVerificationService.request(user);

        log.debug("Registered user {}", user.getId());
        return issue(user, true);
    }

    @Transactional(readOnly = true)
    public Session login(LoginRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), passwordEncoder.encode(TIMING_EQUALISER_PASSWORD));
            throw new InvalidCredentialsException();
        }
        // An account created through a provider has no password. Comparing against null
        // would throw; refusing tells the person nothing about which case they hit.
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issue(user, request.remember());
    }

    @Transactional(readOnly = true)
    public Session refresh(String refreshToken, boolean remember) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new NotAuthenticatedException();
        }
        UserEntity user = jwtService
                .parse(refreshToken, TokenType.REFRESH)
                .flatMap(parsed -> userRepository
                        .findById(parsed.userId())
                        // A token minted before a "sign out everywhere" carries the old
                        // version and is refused here.
                        .filter(candidate -> candidate.getTokenVersion() == parsed.tokenVersion()))
                .orElseThrow(NotAuthenticatedException::new);
        // A refresh keeps whatever lifetime the original sign-in chose: the presented token
        // proves nothing about that choice, so re-reading it from the request would let a
        // client silently upgrade a session cookie into a month-long one.
        return issue(user, remember);
    }

    /** Signs in a user the caller has already authenticated some other way. */
    @Transactional(readOnly = true)
    public Session issueFor(UserEntity user) {
        return issue(user, true);
    }

    /** Invalidates every outstanding refresh token for this user, on every device. */
    @Transactional
    public void signOutEverywhere(UserEntity user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        log.debug("Revoked all sessions for user {}", user.getId());
    }

    /**
     * Renames the account.
     *
     * No token is touched: the name is not part of what a token proves, and the access
     * token the client already holds stays valid, so a rename does not interrupt a sync.
     * Blank means "no name" -- the same null the account had before it was ever given one.
     */
    @Transactional
    public UserDto updateProfile(UserEntity user, String displayName) {
        String trimmed = displayName == null ? null : displayName.trim();
        user.setDisplayName(trimmed == null || trimmed.isEmpty() ? null : trimmed);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        log.debug("Renamed user {}", user.getId());
        return toDto(user);
    }

    /**
     * Deletes the account and everything the server holds for it.
     *
     * The photo objects are removed first: the rows go with the user by cascade, and once
     * they are gone nothing remembers which objects in the bucket were ever hers.
     */
    @Transactional
    public void deleteAccount(UserEntity user) {
        // Read before the rows go, and carried in the event rather than looked up by the
        // listener, which runs after the commit that removed them.
        String recipient = user.getEmail();
        long copies = copyRepository.countByUserIdAndDeletedAtIsNull(user.getId());
        for (PhotoEntity photo : photoRepository.findAllByUserId(user.getId())) {
            if (photo.getStorageKey() != null) {
                storageService.delete(photo.getStorageKey());
            }
        }
        userRepository.delete(user);
        events.publishEvent(new AccountMailEvent.AccountDeleted(recipient, copies));
        log.info("Deleted account {}", user.getId());
    }

    public static UserDto toDto(UserEntity user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt(),
                user.getEmailVerifiedAt() != null,
                user.getPasswordHash() != null);
    }

    private Session issue(UserEntity user, boolean remember) {
        String refreshToken = jwtService.issueRefreshToken(user, remember);
        // The body's refreshToken is filled in by the controller only for DIRECT clients;
        // browsers get it as a cookie and must not see it here.
        return new Session(new SessionDto(jwtService.issueAccessToken(user), null, toDto(user)), refreshToken, remember);
    }

    /** The body the client sees, plus the refresh token the controller turns into a cookie. */
    public record Session(SessionDto body, String refreshToken, boolean remember) {}
}
