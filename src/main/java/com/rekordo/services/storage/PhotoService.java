package com.rekordo.services.storage;

import com.rekordo.configuration.StorageProperties;
import com.rekordo.entity.PhotoEntity;
import com.rekordo.model.core.PhotoUploadDto;
import com.rekordo.model.exception.PhotoNotFoundException;
import com.rekordo.model.exception.PhotoOwnerRequiredException;
import com.rekordo.model.exception.PhotoTooLargeException;
import com.rekordo.model.exception.UnsupportedPhotoTypeException;
import com.rekordo.repository.CopyRepository;
import com.rekordo.repository.PhotoRepository;
import com.rekordo.services.social.VisibilityService;
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
     *
     * <p>Shared with {@code SyncService}, which has to apply the same list to a content type
     * that arrived through a push rather than an upload. One list, or the endpoint that
     * serves the bytes ends up more permissive than the one that took them.
     */
    public static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");

    /** What a content type outside the allowlist is served and stored as. */
    public static final String FALLBACK_TYPE = "application/octet-stream";

    /**
     * Where one account's photo lives in the bucket.
     *
     * <p>Derived from the two ids rather than accepted from anybody. The key is what decides
     * whose bytes come back, so a client that could choose it could choose to be served, or
     * to delete, somebody else's picture.
     */
    public static String objectKey(UUID userId, UUID photoId) {
        return "%s/%s".formatted(userId, photoId);
    }

    /**
     * The content type this photo may be served as.
     *
     * <p>Anything off the allowlist becomes {@value #FALLBACK_TYPE}. The bytes are served
     * from the same origin as the web app, so a stored {@code text/html} would be a script
     * running as the app itself -- and the response header is the whole of what decides
     * that, since a declared type is not something {@code nosniff} can save anybody from.
     */
    public static String servableType(String stored) {
        return stored != null && ALLOWED_TYPES.contains(stored.toLowerCase()) ? stored.toLowerCase() : FALLBACK_TYPE;
    }

    private final PhotoRepository photoRepository;
    private final CopyRepository copyRepository;
    private final StorageService storageService;
    private final StorageUsageService storageUsageService;
    private final StorageProperties properties;
    private final VisibilityService visibilityService;

    /**
     * Stores the bytes and records the metadata.
     *
     * The client chooses the id, as it does for every other record: the photo already
     * exists on the device before it is ever uploaded, and it must keep its identity.
     */
    @Transactional
    public PhotoUploadDto upload(UUID userId, UUID photoId, UUID copyId, UUID wishId, MultipartFile file) {
        // Checked before a single byte is stored: the object goes to MinIO first, and an
        // upload no record can ever reference is an object nothing will ever clean up.
        if ((copyId == null) == (wishId == null)) {
            throw new PhotoOwnerRequiredException();
        }
        String declared = file.getContentType();
        if (declared == null || !ALLOWED_TYPES.contains(declared.toLowerCase())) {
            throw new UnsupportedPhotoTypeException(String.valueOf(declared));
        }
        // Stored lowercased, so what is written is always exactly what the allowlist holds
        // and the serving side has nothing left to normalise.
        String contentType = declared.toLowerCase();
        if (file.getSize() > properties.maxPhotoBytes()) {
            throw new PhotoTooLargeException(properties.maxPhotoBytes());
        }

        // Both refusals happen before the object is written, and this one needs the row the
        // id may already have: re-uploading a photo overwrites its object rather than adding
        // one, so what it costs is the difference, not the whole file.
        PhotoEntity existing = photoRepository.findById(photoId).orElse(null);
        // The id comes from the client, so it can name a row that is somebody else's. Without
        // this, uploading against an id read off a public shelf would rewrite that row's
        // owner and key -- taking the picture out of its collection and into the caller's.
        // The same 404 as a photo that does not exist, so this cannot be used to ask which
        // ids are real.
        if (existing != null && existing.getUserId() != null && !existing.getUserId().equals(userId)) {
            log.warn("Upload for photo {} refused: it belongs to another account", photoId);
            throw new PhotoNotFoundException(photoId);
        }
        long replacing = existing == null || existing.getStorageKey() == null || existing.getByteSize() == null
                ? 0L
                : existing.getByteSize();
        storageUsageService.requireRoom(userId, file.getSize(), replacing);

        // Namespaced by user so one account's objects are never confusable with another's,
        // even if an id were somehow reused.
        String key = objectKey(userId, photoId);
        try {
            storageService.put(key, file.getInputStream(), file.getSize(), contentType);
        } catch (IOException e) {
            throw new com.rekordo.model.exception.StorageUnavailableException("read upload", e);
        }

        PhotoEntity entity = existing == null ? new PhotoEntity() : existing;
        entity.setId(photoId);
        entity.setUserId(userId);
        entity.setCopyId(copyId);
        entity.setWishId(wishId);
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

        log.debug(
                "Stored photo {} for {} {} ({} bytes)",
                photoId,
                copyId == null ? "wish" : "copy",
                copyId == null ? wishId : copyId,
                file.getSize());
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
                // A row with no storage key names no bytes -- there is nothing to serve, and
                // asking storage for a null key would be a 500 rather than the 404 this is.
                .filter(photo -> photo.getDeletedAt() == null && photo.getStorageKey() != null)
                .orElseThrow(() -> new PhotoNotFoundException(photoId));
        if (!maySee(viewerId, entity)) {
            log.warn("Photo {} refused to viewer {}", photoId, viewerId);
            throw new PhotoNotFoundException(photoId);
        }
        return new Download(
                storageService.get(entity.getStorageKey()),
                servableType(entity.getContentType()),
                entity.getByteSize());
    }

    private boolean maySee(UUID viewerId, PhotoEntity photo) {
        UUID ownerId = photo.getUserId();
        if (ownerId.equals(viewerId)) {
            return true;
        }
        if (!visibilityService.canSeeCollection(viewerId, ownerId)) {
            return false;
        }
        // A wish's picture is nobody else's business. A wishlist has no per-entry
        // visibility of its own, and a shared shelf is a shelf of records somebody owns —
        // so there is no answer here that would let a friend through, and the safe answer
        // is the true one.
        if (photo.getCopyId() == null) {
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
