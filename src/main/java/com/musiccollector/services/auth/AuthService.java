package com.musiccollector.services.auth;

import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.action.LoginRequest;
import com.musiccollector.model.action.RegisterRequest;
import com.musiccollector.model.core.SessionDto;
import com.musiccollector.model.core.TokenType;
import com.musiccollector.model.core.UserDto;
import com.musiccollector.model.exception.EmailAlreadyRegisteredException;
import com.musiccollector.model.exception.InvalidCredentialsException;
import com.musiccollector.model.exception.NotAuthenticatedException;
import com.musiccollector.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        user.setTokenVersion(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.debug("Registered user {}", user.getId());
        return issue(user);
    }

    @Transactional(readOnly = true)
    public Session login(LoginRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), passwordEncoder.encode(TIMING_EQUALISER_PASSWORD));
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issue(user);
    }

    @Transactional(readOnly = true)
    public Session refresh(String refreshToken) {
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
        return issue(user);
    }

    /** Invalidates every outstanding refresh token for this user, on every device. */
    @Transactional
    public void signOutEverywhere(UserEntity user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        log.debug("Revoked all sessions for user {}", user.getId());
    }

    public static UserDto toDto(UserEntity user) {
        return new UserDto(user.getId(), user.getEmail(), user.getCreatedAt());
    }

    private Session issue(UserEntity user) {
        String refreshToken = jwtService.issueRefreshToken(user);
        // The body's refreshToken is filled in by the controller only for DIRECT clients;
        // browsers get it as a cookie and must not see it here.
        return new Session(new SessionDto(jwtService.issueAccessToken(user), null, toDto(user)), refreshToken);
    }

    /** The body the client sees, plus the refresh token the controller turns into a cookie. */
    public record Session(SessionDto body, String refreshToken) {}
}
