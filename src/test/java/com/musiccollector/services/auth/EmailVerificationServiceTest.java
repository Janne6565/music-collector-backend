package com.musiccollector.services.auth;

import com.musiccollector.entity.EmailVerificationEntity;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.exception.InvalidVerificationTokenException;
import com.musiccollector.repository.EmailVerificationRepository;
import com.musiccollector.repository.UserRepository;
import com.musiccollector.services.mail.AccountMailEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationRepository verificationRepository;
    @Mock private ApplicationEventPublisher events;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(userRepository, verificationRepository, events);
    }

    private UserEntity user(String email, Instant verifiedAt) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setTokenVersion(0);
        user.setEmailVerifiedAt(verifiedAt);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    private EmailVerificationEntity verification(UUID userId, Instant expiresAt, Instant usedAt) {
        EmailVerificationEntity entity = new EmailVerificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setTokenHash("hash");
        entity.setExpiresAt(expiresAt);
        entity.setUsedAt(usedAt);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    @Test
    void issuesALinkAndStoresOnlyItsHash() {
        service.request(user("jonas@example.test", null));

        ArgumentCaptor<EmailVerificationEntity> saved = ArgumentCaptor.forClass(EmailVerificationEntity.class);
        verify(verificationRepository).save(saved.capture());
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());

        var event = (AccountMailEvent.EmailConfirmationRequested) published.getValue();
        assertThat(event.token()).isNotBlank().isNotEqualTo(saved.getValue().getTokenHash());
    }

    @Test
    void staysSilentWhenThereIsNothingToConfirm() {
        service.request(user("jonas@example.test", Instant.now()));
        // Apple and Google may withhold an address; the placeholder is not a mailbox.
        service.request(user("001abc@no-email.invalid", null));

        verify(verificationRepository, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void confirmingStampsTheAccount() {
        UserEntity user = user("jonas@example.test", null);
        when(verificationRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(verification(user.getId(), Instant.now().plusSeconds(600), null)));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.confirm("whatever");

        assertThat(user.getEmailVerifiedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void aSecondConfirmationDoesNotRestampTheDate() {
        Instant original = Instant.parse("2026-01-01T00:00:00Z");
        UserEntity user = user("jonas@example.test", original);
        when(verificationRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(verification(user.getId(), Instant.now().plusSeconds(600), null)));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.confirm("whatever");

        // When the address was proved is a fact; a link clicked twice is not a second proof.
        assertThat(user.getEmailVerifiedAt()).isEqualTo(original);
        verify(userRepository, never()).save(any());
    }

    @Test
    void aUsedExpiredOrUnknownLinkIsRefused() {
        when(verificationRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(verification(UUID.randomUUID(), Instant.now().plusSeconds(600), Instant.now())))
                .thenReturn(Optional.of(verification(UUID.randomUUID(), Instant.now().minusSeconds(1), null)))
                .thenReturn(Optional.empty());

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> service.confirm("whatever"))
                    .isInstanceOf(InvalidVerificationTokenException.class);
        }
    }
}
