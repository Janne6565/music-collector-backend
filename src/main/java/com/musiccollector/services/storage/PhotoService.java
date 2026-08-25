package com.musiccollector.services.storage;

import com.musiccollector.configuration.StorageProperties;
import com.musiccollector.entity.PhotoEntity;
import com.musiccollector.model.core.PhotoUploadDto;
import com.musiccollector.model.exception.PhotoNotFoundException;
import com.musiccollector.model.exception.PhotoTooLargeException;
import com.musiccollector.model.exception.UnsupportedPhotoTypeException;
import com.musiccollector.repository.CopyRepository;
import com.musiccollector.repository.PhotoRepository;
import com.musiccollector.services.social.VisibilityService;
import io.minio.GetObjectResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    /**
     * What a phone camera or a file picker actually produces. Deliberately a allowlist:
     * accepting whatever arrives would let this endpoint store anything at all.
     */
    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");

    private final PhotoRepository photoRepository;
    private final CopyRepository copyRepository;
    private final StorageService storageService;
    private final StorageProperties properties;
    private final VisibilityService visibilityService;

    /**
     * Stores the bytes and records the metadata.
     *
     * The client chooses the id, as it does for every other record: the photo already
     * exists on the device before it is ever uploaded, and it must keep its identity.
     */
    @Transactional
    public PhotoUploadDto upload(UUID userId, UUID photoId, UUID copyId, MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new UnsupportedPhotoTypeException(String.valueOf(contentType));
        }
        if (file.getSize() > properties.maxUploadBytes()) {
            throw new PhotoTooLargeException(properties.maxUploadBytes());
        }

        // Namespaced by user so one account's objects are never confusable with another's,
        // even if an id were somehow reused.
        String key = "%s/%s".formatted(userId, photoId);
        try {
            storageService.put(key, file.getInputStream(), file.getSize(), contentType);
        } catch (IOException e) {
            throw new com.musiccollector.model.exception.StorageUnavailableException("read upload", e);
        }

        PhotoEntity entity = photoRepository.findById(photoId).orElseGet(PhotoEntity::new);
        entity.setId(photoId);
        entity.setUserId(userId);
        entity.setCopyId(copyId);
        entity.setStorageKey(key);
        entity.setContentType(contentType);
        entity.setByteSize(file.getSize());
        if (entity.getSortIndex() == null) {
            entity.setSortIndex(0);
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(System.currentTimeMillis());
        }
        if (entity.getFieldClocks() == null) {
            // Filled in properly when the client pushes the record through sync; this is
            // only so the row is valid if the upload lands first.
            entity.setFieldClocks("{}");
        }
        entity.setSyncSeq(0L);
        photoRepository.save(entity);

        log.debug("Stored photo {} for copy {} ({} bytes)", photoId, copyId, file.getSize());
        return new PhotoUploadDto(photoId.toString(), key, contentType, file.getSize());
    }

    /**
     * The bytes, for whoever is allowed to have them.
     *
     * <p>The gate is here rather than in the security config because it is not a property
     * of the path: the same URL is served to the owner always, to a friend when the shelf
     * is open to friends, and to a signed-out stranger only when the owner made the
     * collection public. {@code viewerId} is null for that stranger.
     *
     * <p>Every refusal is a 404, not a 403. A distinguishable "you may not have this"
     * would turn the endpoint into a way to confirm which photo ids exist.
     *
     * @param viewerId who is asking, or null for a signed-out visitor
     */
    @Transactional(readOnly = true)
    public Download download(UUID viewerId, UUID photoId) {
        PhotoEntity entity = photoRepository
                .findById(photoId)
                .filter(photo -> photo.getDeletedAt() == null)
                .orElseThrow(() -> new PhotoNotFoundException(photoId));
        if (!maySee(viewerId, entity)) {
            log.warn("Photo {} refused to viewer {}", photoId, viewerId);
            throw new PhotoNotFoundException(photoId);
        }
        return new Download(storageService.get(entity.getStorageKey()), entity.getContentType(), entity.getByteSize());
    }

    private boolean maySee(UUID viewerId, PhotoEntity photo) {
        UUID ownerId = photo.getUserId();
        if (ownerId.equals(viewerId)) {
            return true;
        }
        if (!visibilityService.canSeeCollection(viewerId, ownerId)) {
            return false;
        }
        // The copy's own answer still applies. A picture of a copy hidden one by one is
        // hidden with it, or hiding a record would leave its sleeve reachable by URL.
        return copyRepository
                .findById(photo.getCopyId())
                .filter(copy -> copy.getUserId().equals(ownerId))
                .filter(copy -> copy.getDeletedAt() == null)
                .filter(copy -> !copy.isHidden())
                .isPresent();
    }

    public record Download(GetObjectResponse stream, String contentType, long byteSize) {}
}
