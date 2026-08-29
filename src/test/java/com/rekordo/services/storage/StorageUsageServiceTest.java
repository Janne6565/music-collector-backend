package com.rekordo.services.storage;

import com.rekordo.configuration.StorageProperties;
import com.rekordo.entity.UserEntity;
import com.rekordo.model.core.StorageUsageDto;
import com.rekordo.model.exception.StorageQuotaExceededException;
import com.rekordo.repository.PhotoRepository;
import com.rekordo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageUsageServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final long QUOTA = 20_971_520;

    @Mock private PhotoRepository photoRepository;
    @Mock private UserRepository userRepository;

    private StorageUsageService service;

    @BeforeEach
    void setUp() {
        service = new StorageUsageService(
                photoRepository,
                userRepository,
                new StorageProperties("http://s", "a", "b", "bucket", 15_728_640, 5_242_880, QUOTA));
    }

    @Test
    void addsTheProfilePictureToTheSleevePhotos() {
        givenPhotos(1_000_000, 4);
        givenAvatar(50_000L);

        StorageUsageDto usage = service.usage(USER);

        assertThat(usage.photoBytes()).isEqualTo(1_000_000);
        assertThat(usage.photoCount()).isEqualTo(4);
        assertThat(usage.avatarBytes()).isEqualTo(50_000);
        assertThat(usage.usedBytes()).isEqualTo(1_050_000);
        assertThat(usage.quotaBytes()).isEqualTo(QUOTA);
    }

    @Test
    void countsAPictureFromBeforeTheColumnExistedAsNothing() {
        // V37 leaves avatar_bytes NULL for the few pictures that predate it. Fifty kilobytes
        // is a quarter of a percent of the allowance, so the miscount can never be the reason
        // an upload is refused -- and it corrects itself the next time the picture changes.
        givenPhotos(1_000, 1);
        UserEntity user = new UserEntity();
        user.setAvatarKey("avatars/" + USER);
        user.setAvatarBytes(null);
        when(userRepository.findById(USER)).thenReturn(Optional.of(user));

        assertThat(service.usage(USER).avatarBytes()).isZero();
    }

    @Test
    void letsAnUploadThroughWhenItFits() {
        givenPhotos(QUOTA - 1_000, 20);
        givenNoAvatar();

        assertThatCode(() -> service.requireRoom(USER, 1_000, 0)).doesNotThrowAnyException();
    }

    @Test
    void refusesTheUploadThatWouldCrossTheLine() {
        givenPhotos(QUOTA - 1_000, 20);
        givenNoAvatar();

        assertThatThrownBy(() -> service.requireRoom(USER, 1_001, 0))
                .isInstanceOf(StorageQuotaExceededException.class);
    }

    @Test
    void doesNotChargeTwiceForAnObjectBeingWrittenOver() {
        // A full account replacing one of its own photos with a smaller one is not asking
        // for more room, and refusing it would leave the only way out of "full" closed.
        givenPhotos(QUOTA, 30);
        givenNoAvatar();

        assertThatCode(() -> service.requireRoom(USER, 500, 2_000)).doesNotThrowAnyException();
    }

    @Test
    void leavesAnAccountAlreadyOverTheLineAloneAndOnlyStopsItGrowing() {
        // Originals uploaded by an older app version, before any of this existed. Nothing is
        // deleted on their behalf; they simply cannot add until they delete something.
        givenPhotos(QUOTA * 2, 40);
        givenNoAvatar();

        assertThatThrownBy(() -> service.requireRoom(USER, 1, 0))
                .isInstanceOf(StorageQuotaExceededException.class);
        assertThat(service.usage(USER).usedBytes()).isEqualTo(QUOTA * 2);
    }

    private void givenPhotos(long bytes, long count) {
        when(photoRepository.sumLiveBytes(USER)).thenReturn(new PhotoRepository.Usage() {
            @Override
            public long getBytes() {
                return bytes;
            }

            @Override
            public long getPhotos() {
                return count;
            }
        });
    }

    private void givenAvatar(Long bytes) {
        UserEntity user = new UserEntity();
        user.setAvatarKey("avatars/" + USER);
        user.setAvatarBytes(bytes);
        when(userRepository.findById(USER)).thenReturn(Optional.of(user));
    }

    private void givenNoAvatar() {
        when(userRepository.findById(USER)).thenReturn(Optional.of(new UserEntity()));
    }
}
