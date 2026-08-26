package com.musiccollector.services.notifications;

import com.musiccollector.entity.NotificationPreferenceEntity;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.core.NotificationCategory;
import com.musiccollector.model.core.NotificationPreferenceDto;
import com.musiccollector.model.core.NotificationPreferencesDto;
import com.musiccollector.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationPreferenceServiceTest {

    @Mock private NotificationPreferenceRepository repository;

    private NotificationPreferenceService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(repository);
        user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("jonas@example.test");
        user.setPasswordHash("hash");
        user.setTokenVersion(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        when(repository.findAllByUserId(any())).thenReturn(List.of());
    }

    private NotificationPreferenceDto row(NotificationPreferencesDto grid, NotificationCategory category) {
        return grid.categories().stream()
                .filter(c -> c.category() == category)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void anAccountThatNeverOpenedTheScreenStillGetsTheWholeGrid() {
        // Storing the defaults would mean a new category needs a backfill before anybody's
        // screen reads right, so the answer is assembled rather than read.
        NotificationPreferencesDto grid = service.forUser(user);

        assertThat(grid.categories()).hasSize(NotificationCategory.values().length);
        assertThat(row(grid, NotificationCategory.FRIEND_REQUEST).mail()).isTrue();
        assertThat(row(grid, NotificationCategory.FRIEND_REQUEST).push()).isTrue();
        // Board 22c killed the per-record activity push outright; the digest push is opt-in.
        assertThat(row(grid, NotificationCategory.FRIEND_ACTIVITY).mail()).isTrue();
        assertThat(row(grid, NotificationCategory.FRIEND_ACTIVITY).push()).isFalse();
        // Off until somebody asks for it.
        assertThat(row(grid, NotificationCategory.PRODUCT_NEWS).mail()).isFalse();
    }

    @Test
    void securityMailCannotBeSwitchedOff() {
        NotificationPreferencesDto grid = service.update(user, NotificationCategory.SECURITY, false, false);

        // A notice you can silence is not a notice -- and that is not enforced in a client.
        assertThat(row(grid, NotificationCategory.SECURITY).mail()).isTrue();
        assertThat(row(grid, NotificationCategory.SECURITY).mailLocked()).isTrue();
        ArgumentCaptor<NotificationPreferenceEntity> saved =
                ArgumentCaptor.forClass(NotificationPreferenceEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().isMail()).isTrue();
        // The lock covers mail only; whether it also buzzes is the account holder's to set.
        assertThat(saved.getValue().isPush()).isFalse();
    }

    @Test
    void mailEnabledReadsTheStoredChoiceAndFallsBackToTheDefault() {
        assertThat(service.mailEnabled(user, NotificationCategory.PRODUCT_NEWS)).isFalse();

        NotificationPreferenceEntity row = new NotificationPreferenceEntity();
        row.setUserId(user.getId());
        row.setCategory(NotificationCategory.PRODUCT_NEWS);
        row.setMail(true);
        when(repository.findAllByUserId(user.getId())).thenReturn(List.of(row));

        assertThat(service.mailEnabled(user, NotificationCategory.PRODUCT_NEWS)).isTrue();
        // Locked categories never consult a row at all.
        assertThat(service.mailEnabled(user, NotificationCategory.SECURITY)).isTrue();
    }

    @Test
    void theGridSaysPushHasNowhereToArrive() {
        // There is no push transport yet, so the column reports that plainly rather than
        // offering switches that would silently do nothing (22a). The choices persist.
        assertThat(service.forUser(user).pushAvailable()).isFalse();
    }
}
