package com.musiccollector.services.auth;

import com.musiccollector.entity.PasswordResetEntity;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.exception.InvalidResetTokenException;
import com.musiccollector.repository.PasswordResetRepository;
import com.musiccollector.repository.UserRepository;
import com.musiccollector.services.mail.AccountMailEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetRepository resetRepository;
    @Mock private ApplicationEventPublisher events;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, resetRepository, new BCryptPasswordEncoder(), events);
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("jonas@example.test");
        user.setPasswordHash("old-hash");
        user.setTokenVersion(3);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    @Test
    void sendsALinkAndStoresOnlyItsHash() {
        UserEntity user = user();
        when(userRepository.findByEmailIgnoreCase("jonas@example.test")).thenReturn(Optional.of(user));

        service.request("jonas@example.test");

        ArgumentCaptor<PasswordResetEntity> saved = ArgumentCaptor.forClass(PasswordResetEntity.class);
        verify(resetRepository).save(saved.capture());
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());

        // The raw token goes out in the event and only its hash is stored, so a database
        // leak is not a pile of account takeovers.
        var event = (AccountMailEvent.PasswordResetRequested) published.getValue();
        assertThat(event.recipient()).isEqualTo("jonas@example.test");
        assertThat(event.token()).isNotBlank().isNotEqualTo(saved.getValue().getTokenHash());
        assertThat(saved.getValue().getUsedAt()).isNull();
    }

    @Test
    void staysSilentForAnAddressWithNoAccount() {
        // Behaving differently here would turn this endpoint into a way to find out who is
        // registered.
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        service.request("nobody@example.test");

        verify(events, never()).publishEvent(any(Object.class));
        verify(resetRepository, never()).save(any());
    }

    private PasswordResetEntity reset(UUID userId, Instant expiresAt, Instant usedAt) {
        PasswordResetEntity entity = new PasswordResetEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setTokenHash("hash");
        entity.setExpiresAt(expiresAt);
        entity.setUsedAt(usedAt);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    @Test
    void redeemingChangesThePasswordAndRevokesEverySession() {
        UserEntity user = user();
        when(resetRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(reset(user.getId(), Instant.now().plusSeconds(600), null)));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.redeem("whatever", "a-brand-new-passphrase");

        assertThat(user.getPasswordHash()).isNotEqualTo("old-hash");
        // A reset may be locking somebody else out; leaving them signed in would defeat it.
        assertThat(user.getTokenVersion()).isEqualTo(4);
        // Nobody else can tell the account holder that this happened.
        verify(events).publishEvent(any(AccountMailEvent.PasswordChanged.class));
    }

    @Test
    void aLinkWorksOnlyOnce() {
        when(resetRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(reset(UUID.randomUUID(), Instant.now().plusSeconds(600), Instant.now())));

        assertThatThrownBy(() -> service.redeem("whatever", "a-brand-new-passphrase"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void anExpiredLinkIsRefused() {
        when(resetRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(reset(UUID.randomUUID(), Instant.now().minusSeconds(1), null)));

        assertThatThrownBy(() -> service.redeem("whatever", "a-brand-new-passphrase"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void anUnknownTokenIsRefused() {
        when(resetRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem("guessed", "a-brand-new-passphrase"))
                .isInstanceOf(InvalidResetTokenException.class);
    }
}
