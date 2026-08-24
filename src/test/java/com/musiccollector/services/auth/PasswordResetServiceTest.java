package com.musiccollector.services.auth;

import com.musiccollector.configuration.MailProperties;
import com.musiccollector.entity.PasswordResetEntity;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.exception.InvalidResetTokenException;
import com.musiccollector.repository.PasswordResetRepository;
import com.musiccollector.repository.UserRepository;
import com.musiccollector.services.mail.MailPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    @Mock private MailPort mailPort;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                userRepository,
                resetRepository,
                new BCryptPasswordEncoder(),
                mailPort,
                new MailProperties("http://mail", "key", "https://music.example"));
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
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mailPort).send(any(), any(), any(), text.capture());

        // The raw token is in the mail and nowhere else, so a database leak is not a pile
        // of account takeovers.
        assertThat(text.getValue()).contains("https://music.example/reset?token=");
        assertThat(text.getValue()).doesNotContain(saved.getValue().getTokenHash());
        assertThat(saved.getValue().getUsedAt()).isNull();
    }

    @Test
    void staysSilentForAnAddressWithNoAccount() {
        // Behaving differently here would turn this endpoint into a way to find out who is
        // registered.
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        service.request("nobody@example.test");

        verify(mailPort, never()).send(any(), any(), any(), any());
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
