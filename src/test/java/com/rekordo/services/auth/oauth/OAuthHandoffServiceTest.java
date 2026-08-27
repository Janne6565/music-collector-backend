package com.rekordo.services.auth.oauth;

import com.rekordo.entity.OAuthHandoffEntity;
import com.rekordo.entity.UserEntity;
import com.rekordo.model.exception.OAuthFailedException;
import com.rekordo.repository.OAuthHandoffRepository;
import com.rekordo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthHandoffServiceTest {

    @Mock private OAuthHandoffRepository handoffRepository;
    @Mock private UserRepository userRepository;

    private final Map<String, OAuthHandoffEntity> stored = new HashMap<>();
    private OAuthHandoffService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new OAuthHandoffService(handoffRepository, userRepository);
        user = new UserEntity();
        user.setId(UUID.randomUUID());
    }

    /** Stands in for the table, so a code can actually be issued and then redeemed. */
    private void withStorage() {
        recordingSaves();
        when(handoffRepository.findById(any()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
    }

    private void recordingSaves() {
        when(handoffRepository.save(any())).thenAnswer(call -> {
            OAuthHandoffEntity entity = call.getArgument(0);
            stored.put(entity.getCodeHash(), entity);
            return entity;
        });
    }

    @Test
    void aFreshCodeRedeemsToWhoeverSignedIn() {
        withStorage();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThat(service.redeem(service.issue(user))).isEqualTo(user);
    }

    @Test
    void theRawCodeIsNeverStored() {
        // A pending handoff sitting in a leaked database must not be a session waiting to
        // be claimed, exactly as for a password reset token.
        recordingSaves();

        String code = service.issue(user);

        assertThat(stored).doesNotContainKey(code);
        assertThat(stored.keySet()).allSatisfy(hash -> assertThat(hash).isNotEqualTo(code));
    }

    @Test
    void aCodeWorksOnlyOnce() {
        withStorage();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        String code = service.issue(user);
        service.redeem(code);

        // Otherwise anyone who saw the deep link in a log could mint a second session.
        assertThatThrownBy(() -> service.redeem(code)).isInstanceOf(OAuthFailedException.class);
    }

    @Test
    void anExpiredCodeIsRefused() {
        OAuthHandoffEntity expired = new OAuthHandoffEntity();
        expired.setUserId(user.getId());
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        expired.setCreatedAt(Instant.now());
        when(handoffRepository.findById(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.redeem("whatever")).isInstanceOf(OAuthFailedException.class);
    }

    @Test
    void anUnknownCodeIsRefused() {
        when(handoffRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem("nonsense")).isInstanceOf(OAuthFailedException.class);
    }

    @Test
    void aMissingCodeIsRefusedRatherThanThrowing() {
        when(handoffRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem(null)).isInstanceOf(OAuthFailedException.class);
    }
}
