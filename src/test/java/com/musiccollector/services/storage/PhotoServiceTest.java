package com.musiccollector.services.storage;

import com.musiccollector.configuration.StorageProperties;
import com.musiccollector.entity.PhotoEntity;
import com.musiccollector.model.core.PhotoUploadDto;
import com.musiccollector.model.exception.PhotoNotFoundException;
import com.musiccollector.model.exception.PhotoTooLargeException;
import com.musiccollector.model.exception.UnsupportedPhotoTypeException;
import com.musiccollector.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    private static final long MAX_BYTES = 1_000_000;

    @Mock private PhotoRepository photoRepository;
    @Mock private StorageService storageService;

    private PhotoService service;

    @BeforeEach
    void setUp() {
        service = new PhotoService(
                photoRepository,
                storageService,
                new StorageProperties("http://localhost:9000", "a", "b", "bucket", MAX_BYTES));
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
    void refusesToServeAPhotoBelongingToSomebodyElse() {
        // Scoped by user in the query, so a guessed id reveals nothing.
        when(photoRepository.findByIdAndUserId(PHOTO, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(USER, PHOTO)).isInstanceOf(PhotoNotFoundException.class);
    }

    @Test
    void refusesToServeADeletedPhoto() {
        PhotoEntity deleted = new PhotoEntity();
        deleted.setId(PHOTO);
        deleted.setDeletedAt(9000L);
        when(photoRepository.findByIdAndUserId(PHOTO, USER)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.download(USER, PHOTO)).isInstanceOf(PhotoNotFoundException.class);
    }
}
