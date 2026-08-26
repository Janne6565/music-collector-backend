package com.musiccollector.services.auth;

import com.musiccollector.entity.EmailVerificationEntity;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.exception.EmailAlreadyRegisteredException;
import com.musiccollector.model.exception.InvalidCredentialsException;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationRepository verificationRepository;
    @Mock private ApplicationEventPublisher events;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(userRepository, verificationRepository, encoder, events);
        when(verificationRepository.findOutstanding(any())).thenReturn(List.of());
    }

    private UserEntity user(String email, Instant verifiedAt) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode("a-brand-new-passphrase"));
        user.setTokenVersion(2);
        user.setEmailVerifiedAt(verifiedAt);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    private EmailVerificationEntity outstanding(UUID userId, Instant createdAt) {
        EmailVerificationEntity entity = new EmailVerificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setTokenHash("hash");
        entity.setExpiresAt(createdAt.plusSeconds(86_400));
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private <T> T published(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + type.getSimpleName() + " was published"));
    }

    // ------------------------------------------------------------ confirming

    @Test
    void issuesALinkAndStoresOnlyItsHash() {
        service.request(user("jonas@example.test", null));

        ArgumentCaptor<EmailVerificationEntity> saved = ArgumentCaptor.forClass(EmailVerificationEntity.class);
        verify(verificationRepository).save(saved.capture());
        assertThat(published(AccountMailEvent.EmailConfirmationRequested.class).token())
                .isNotBlank()
                .isNotEqualTo(saved.getValue().getTokenHash());
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
    void pressedTwiceInsideAMinuteSendsNothingAndCountsDown() {
        UserEntity user = user("jonas@example.test", null);
        when(verificationRepository.findOutstanding(user.getId()))
                .thenReturn(List.of(outstanding(user.getId(), Instant.now().minusSeconds(13))));

        var status = service.request(user);

        // The first link is still the valid one, and pressing again is impatience rather
        // than a mistake -- so it is answered by a countdown, not a duplicate or an error.
        verify(verificationRepository, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
        assertThat(status.retryAfter()).isBetween(40L, 60L);
    }

    @Test
    void issuingALinkRetiresTheOneBefore() {
        UserEntity user = user("jonas@example.test", null);
        EmailVerificationEntity older = outstanding(user.getId(), Instant.now().minusSeconds(600));
        when(verificationRepository.findOutstanding(user.getId())).thenReturn(List.of(older));

        service.request(user);

        // Two live links would mean the older mail sometimes works and sometimes does not.
        assertThat(older.getUsedAt()).isNotNull();
    }

    @Test
    void confirmingStampsTheAccountAndASecondClickDoesNotRestampIt() {
        Instant original = Instant.parse("2026-01-01T00:00:00Z");
        UserEntity fresh = user("jonas@example.test", null);
        when(verificationRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(outstanding(fresh.getId(), Instant.now())));
        when(userRepository.findById(fresh.getId())).thenReturn(Optional.of(fresh));
        service.confirm("whatever");
        assertThat(fresh.getEmailVerifiedAt()).isNotNull();

        UserEntity already = user("jonas@example.test", original);
        when(verificationRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(outstanding(already.getId(), Instant.now())));
        when(userRepository.findById(already.getId())).thenReturn(Optional.of(already));
        service.confirm("whatever");

        // When the address was proved is a fact; a link clicked twice is not a second proof.
        assertThat(already.getEmailVerifiedAt()).isEqualTo(original);
    }

    @Test
    void aUsedExpiredOrUnknownLinkIsRefused() {
        EmailVerificationEntity used = outstanding(UUID.randomUUID(), Instant.now());
        used.setUsedAt(Instant.now());
        EmailVerificationEntity expired = outstanding(UUID.randomUUID(), Instant.now().minusSeconds(200_000));
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(verificationRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(used))
                .thenReturn(Optional.of(expired))
                .thenReturn(Optional.empty());

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> service.confirm("whatever"))
                    .isInstanceOf(InvalidVerificationTokenException.class);
        }
    }

    // ------------------------------------------------------------ changing

    @Test
    void aChangeTellsBothMailboxesAndMovesNothingYet() {
        UserEntity user = user("jonas@example.test", Instant.now());

        service.requestChange(user, "j.meyer@posteo.de", "a-brand-new-passphrase");

        // The old address goes on signing you in until the new one answers.
        assertThat(user.getEmail()).isEqualTo("jonas@example.test");
        assertThat(published(AccountMailEvent.EmailChangeRequested.class).recipient())
                .isEqualTo("j.meyer@posteo.de");
        var notice = published(AccountMailEvent.EmailChangeStarted.class);
        assertThat(notice.recipient()).isEqualTo("jonas@example.test");
        assertThat(notice.newEmail()).isEqualTo("j.meyer@posteo.de");
        assertThat(notice.cancelToken()).isNotBlank();
    }

    @Test
    void changingAwayFromAnUnconfirmedAddressWarnsNobody() {
        // The common case is a typo, and there is nobody at the old mailbox to defend --
        // nothing was ever shown to be there.
        service.requestChange(user("typo@example.test", null), "j.meyer@posteo.de", "a-brand-new-passphrase");

        assertThatThrownBy(() -> published(AccountMailEvent.EmailChangeStarted.class))
                .isInstanceOf(AssertionError.class);
        assertThat(published(AccountMailEvent.EmailChangeRequested.class).recipient())
                .isEqualTo("j.meyer@posteo.de");
    }

    @Test
    void aChangeNeedsThePasswordUnlessThereIsNone() {
        assertThatThrownBy(() ->
                        service.requestChange(user("jonas@example.test", Instant.now()), "j@posteo.de", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);

        // An account made through a provider has no password to ask for; refusing would
        // strand it at an address it never chose.
        UserEntity provider = user("jonas@example.test", Instant.now());
        provider.setPasswordHash(null);
        service.requestChange(provider, "j.meyer@posteo.de", null);
        assertThat(published(AccountMailEvent.EmailChangeRequested.class)).isNotNull();
    }

    @Test
    void anAddressSomebodyElseHasIsRefused() {
        when(userRepository.existsByEmailIgnoreCase("taken@example.test")).thenReturn(true);

        assertThatThrownBy(() -> service.requestChange(
                        user("jonas@example.test", Instant.now()), "taken@example.test", "a-brand-new-passphrase"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void confirmingAChangeMovesTheAccount() {
        UserEntity user = user("jonas@example.test", Instant.now());
        EmailVerificationEntity change = outstanding(user.getId(), Instant.now());
        change.setNewEmail("j.meyer@posteo.de");
        change.setPreviousEmail("jonas@example.test");
        when(verificationRepository.findByTokenHash(any())).thenReturn(Optional.of(change));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.confirm("whatever");

        assertThat(user.getEmail()).isEqualTo("j.meyer@posteo.de");
        assertThat(user.getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void anAddressClaimedWhileTheLinkWaitedIsRefused() {
        UserEntity user = user("jonas@example.test", Instant.now());
        EmailVerificationEntity change = outstanding(user.getId(), Instant.now());
        change.setNewEmail("j.meyer@posteo.de");
        when(verificationRepository.findByTokenHash(any())).thenReturn(Optional.of(change));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase("j.meyer@posteo.de")).thenReturn(true);

        assertThatThrownBy(() -> service.confirm("whatever")).isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(user.getEmail()).isEqualTo("jonas@example.test");
    }

    @Test
    void cancellingAfterTheChangeLandedPutsTheAddressBackAndSignsEverybodyOut() {
        UserEntity user = user("j.meyer@posteo.de", Instant.now());
        EmailVerificationEntity change = outstanding(user.getId(), Instant.now());
        change.setNewEmail("j.meyer@posteo.de");
        change.setPreviousEmail("jonas@example.test");
        change.setUsedAt(Instant.now());
        change.setCancelTokenHash("cancel-hash");
        change.setCancelExpiresAt(Instant.now().plusSeconds(3600));
        when(verificationRepository.findByCancelTokenHash(any())).thenReturn(Optional.of(change));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.cancelChange("whatever");

        assertThat(user.getEmail()).isEqualTo("jonas@example.test");
        // Whoever asked for the change is holding a session; undoing a hijack that leaves
        // them signed in has been undone in name only.
        assertThat(user.getTokenVersion()).isEqualTo(3);
        assertThat(change.getCancelTokenHash()).isNull();
    }

    @Test
    void callingOffAChangeInTheAppTakesTheUndoWithIt() {
        UserEntity user = user("jonas@example.test", Instant.now());
        EmailVerificationEntity change = outstanding(user.getId(), Instant.now());
        change.setNewEmail("j.meyer@posteo.de");
        change.setCancelTokenHash("cancel-hash");
        change.setCancelExpiresAt(Instant.now().plusSeconds(3600));
        when(verificationRepository.findOutstanding(user.getId())).thenReturn(List.of(change));

        service.cancelPendingChange(user);

        // There is nothing left to undo, and a cancel link that still worked would sign
        // every device out over a change that never happened.
        assertThat(change.getUsedAt()).isNotNull();
        assertThat(change.getCancelTokenHash()).isNull();
    }

    @Test
    void anExpiredCancelLinkIsRefused() {
        EmailVerificationEntity change = outstanding(UUID.randomUUID(), Instant.now());
        change.setCancelTokenHash("cancel-hash");
        change.setCancelExpiresAt(Instant.now().minusSeconds(1));
        when(verificationRepository.findByCancelTokenHash(any())).thenReturn(Optional.of(change));

        assertThatThrownBy(() -> service.cancelChange("whatever"))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }
}
