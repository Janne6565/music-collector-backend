package com.musiccollector.services.storage;

import com.musiccollector.configuration.StorageProperties;
import com.musiccollector.entity.PhotoEntity;
import com.musiccollector.model.core.PhotoUploadDto;
import com.musiccollector.model.exception.PhotoNotFoundException;
import com.musiccollector.model.exception.PhotoTooLargeException;
import com.musiccollector.model.exception.UnsupportedPhotoTypeException;
import com.musiccollector.entity.CopyEntity;
import com.musiccollector.repository.CopyRepository;
import com.musiccollector.repository.PhotoRepository;
import com.musiccollector.services.social.VisibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID PHOTO = UUID.randomUUID();
    private static final UUID COPY = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final long MAX_BYTES = 1_000_000;

    @Mock private PhotoRepository photoRepository;
    @Mock private CopyRepository copyRepository;
    @Mock private StorageService storageService;
    @Mock private VisibilityService visibilityService;

    private PhotoService service;

    @BeforeEach
    void setUp() {
        service = new PhotoService(
                photoRepository,
                copyRepository,
                storageService,
                new StorageProperties("http://localhost:9000", "a", "b", "bucket", MAX_BYTES),
                visibilityService);
    }

    private MockMultipartFile file(String contentType, int bytes) {
        return new MockMultipartFile("file", "sleeve.jpg", contentType, new byte[bytes]);
    }

    @Test
    void storesTheBytesUnderAKeyNamespacedByUser() {
        when(photoRepository.findById(PHOTO)).thenReturn(Optional.empty());
        when(photoRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        PhotoUploadDto result = service.upload(USER, PHOTO, COPY, file("image/jpeg", 128));

        // Namespaced so one account's objects can never be confused with another's.
        assertThat(result.storageKey()).isEqualTo(USER + "/" + PHOTO);
        assertThat(result.byteSize()).isEqualTo(128);
    }

    @Test
    void rejectsAnythingThatIsNotAnImageItStores() {
        assertThatThrownBy(() -> service.upload(USER, PHOTO, COPY, file("application/pdf", 10)))
                .isInstanceOf(UnsupportedPhotoTypeException.class);
        assertThatThrownBy(() -> service.upload(USER, PHOTO, COPY, file(null, 10)))
                .isInstanceOf(UnsupportedPhotoTypeException.class);

        // Nothing reaches storage: the check runs before the write, not after it.
        verify(storageService, never()).put(any(), any(), anyLong(), any());
    }

    @Test
    void rejectsAnOversizedUploadBeforeStoringIt() {
        assertThatThrownBy(() -> service.upload(USER, PHOTO, COPY, file("image/jpeg", (int) MAX_BYTES + 1)))
                .isInstanceOf(PhotoTooLargeException.class);

        verify(storageService, never()).put(any(), any(), anyLong(), any());
    }

    @Test
    void acceptsTheFormatsPhoneCamerasProduce() {
        when(photoRepository.findById(any())).thenReturn(Optional.empty());
        when(photoRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        for (String type : new String[] {"image/jpeg", "image/png", "image/webp", "image/heic"}) {
            assertThat(service.upload(USER, UUID.randomUUID(), COPY, file(type, 16)).contentType())
                    .isEqualTo(type);
        }
    }

    @Test
    void refusesToServeAPhotoBelongingToSomebodyElseWhoSharesNothing() {
        when(photoRepository.findById(PHOTO)).thenReturn(Optional.of(photoOf(STRANGER)));
        when(visibilityService.canSeeCollection(USER, STRANGER)).thenReturn(false);

        assertThatThrownBy(() -> service.download(USER, PHOTO)).isInstanceOf(PhotoNotFoundException.class);
    }

    @Test
    void refusesToServeADeletedPhoto() {
        PhotoEntity deleted = photoOf(USER);
        deleted.setDeletedAt(9000L);
        when(photoRepository.findById(PHOTO)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.download(USER, PHOTO)).isInstanceOf(PhotoNotFoundException.class);
    }

    @Test
    void servesAFriendsPhotoWhenTheirCollectionIsOpenToTheViewer() {
        when(photoRepository.findById(PHOTO)).thenReturn(Optional.of(photoOf(STRANGER)));
        when(visibilityService.canSeeCollection(USER, STRANGER)).thenReturn(true);
        when(copyRepository.findById(COPY)).thenReturn(Optional.of(copyOf(STRANGER, false)));
        when(storageService.get(any())).thenReturn(null);

        assertThatCode(() -> service.download(USER, PHOTO)).doesNotThrowAnyException();
    }

    @Test
    void servesAPublicShelfToSomebodyWithNoAccountAtAll() {
        when(photoRepository.findById(PHOTO)).thenReturn(Optional.of(photoOf(STRANGER)));
        when(visibilityService.canSeeCollection(null, STRANGER)).thenReturn(true);
        when(copyRepository.findById(COPY)).thenReturn(Optional.of(copyOf(STRANGER, false)));
        when(storageService.get(any())).thenReturn(null);

        assertThatCode(() -> service.download(null, PHOTO)).doesNotThrowAnyException();
    }

    @Test
    void withholdsThePhotoOfACopyHiddenOneByOne() {
        // The shelf is open, but this record is not. Otherwise hiding a copy would leave its
        // sleeve reachable by anyone who had the URL.
        when(photoRepository.findById(PHOTO)).thenReturn(Optional.of(photoOf(STRANGER)));
        when(visibilityService.canSeeCollection(USER, STRANGER)).thenReturn(true);
        when(copyRepository.findById(COPY)).thenReturn(Optional.of(copyOf(STRANGER, true)));

        assertThatThrownBy(() -> service.download(USER, PHOTO)).isInstanceOf(PhotoNotFoundException.class);
    }

    @Test
    void alwaysServesTheOwnerTheirOwnPhotoWithoutConsultingAnySetting() {
        when(photoRepository.findById(PHOTO)).thenReturn(Optional.of(photoOf(USER)));
        when(storageService.get(any())).thenReturn(null);

        assertThatCode(() -> service.download(USER, PHOTO)).doesNotThrowAnyException();
    }

    private static PhotoEntity photoOf(UUID owner) {
        PhotoEntity photo = new PhotoEntity();
        photo.setId(PHOTO);
        photo.setUserId(owner);
        photo.setCopyId(COPY);
        photo.setStorageKey(owner + "/" + PHOTO);
        photo.setContentType("image/jpeg");
        photo.setByteSize(128L);
        return photo;
    }

    private static CopyEntity copyOf(UUID owner, boolean hidden) {
        CopyEntity copy = new CopyEntity();
        copy.setId(COPY);
        copy.setUserId(owner);
        copy.setHidden(hidden);
        return copy;
    }
}
