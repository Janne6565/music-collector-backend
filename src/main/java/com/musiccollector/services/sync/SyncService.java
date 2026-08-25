package com.musiccollector.services.sync;

import com.musiccollector.entity.CopyEntity;
import com.musiccollector.entity.PhotoEntity;
import com.musiccollector.entity.WishlistItemEntity;
import com.musiccollector.model.core.SyncCopyDto;
import com.musiccollector.model.core.SyncPhotoDto;
import com.musiccollector.model.core.SyncPullDto;
import com.musiccollector.model.core.SyncWishDto;
import com.musiccollector.repository.CopyRepository;
import com.musiccollector.repository.PhotoRepository;
import com.musiccollector.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server's half of sync.
 *
 * The server is a merge participant, not just storage: a pushed copy is merged against
 * what is already stored using the same contract the clients use ({@link CopyMerge}), and
 * the merged result is what gets saved and returned. That is what lets two devices that
 * have never spoken to each other converge through the server.
 */
@Service
@RequiredArgsConstructor
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    /** Bounded so one enormous collection cannot be pulled in a single response. */
    private static final int PULL_PAGE_SIZE = 500;

    private static final TypeReference<Map<String, String>> CLOCKS = new TypeReference<>() {};

    private final CopyRepository copyRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final PhotoRepository photoRepository;
    private final com.musiccollector.services.storage.StorageService storageService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SyncPullDto pull(UUID userId, long since) {
        List<CopyEntity> copies =
                copyRepository.findAllByUserIdAndSyncSeqGreaterThanOrderBySyncSeqAsc(userId, since);
        List<WishlistItemEntity> wishes =
                wishlistItemRepository.findAllByUserIdAndSyncSeqGreaterThanOrderBySyncSeqAsc(userId, since);
        List<PhotoEntity> photos =
                photoRepository.findAllByUserIdAndSyncSeqGreaterThanOrderBySyncSeqAsc(userId, since);

        Page<CopyEntity> copyPage = page(copies, CopyEntity::getSyncSeq);
        Page<WishlistItemEntity> wishPage = page(wishes, WishlistItemEntity::getSyncSeq);
        Page<PhotoEntity> photoPage = page(photos, PhotoEntity::getSyncSeq);
        List<Page<?>> pages = List.of(copyPage, wishPage, photoPage);

        boolean hasMore = pages.stream().anyMatch(Page::truncated);
        long cursor;
        if (hasMore) {
            // A page was cut short, so the cursor must stop at the lowest point every kind
            // is complete up to. Advancing past a record that was not sent would leave it
            // stranded on the server with the client believing it is up to date.
            cursor = pages.stream()
                    .filter(Page::truncated)
                    .mapToLong(Page::lastSeq)
                    .min()
                    .orElse(since);
        } else {
            // Nothing was withheld, so the cursor can take the high-water mark of them all.
            cursor = pages.stream().mapToLong(Page::lastSeq).filter(seq -> seq > 0).max().orElse(since);
            cursor = Math.max(cursor, since);
        }

        final long limit = cursor;
        return new SyncPullDto(
                copyPage.upTo(limit, CopyEntity::getSyncSeq).stream().map(this::toDto).toList(),
                wishPage.upTo(limit, WishlistItemEntity::getSyncSeq).stream().map(this::toWishDto).toList(),
                photoPage.upTo(limit, PhotoEntity::getSyncSeq).stream().map(this::toPhotoDto).toList(),
                limit,
                hasMore);
    }

    /** One kind's slice of a pull, with whether it had to be cut short. */
    private record Page<T>(List<T> rows, boolean truncated, long lastSeq) {
        List<T> upTo(long limit, java.util.function.ToLongFunction<T> seq) {
            return rows.stream().filter(row -> seq.applyAsLong(row) <= limit).toList();
        }
    }

    private <T> Page<T> page(List<T> all, java.util.function.ToLongFunction<T> seq) {
        boolean truncated = all.size() > PULL_PAGE_SIZE;
        List<T> rows = truncated ? all.subList(0, PULL_PAGE_SIZE) : all;
        long lastSeq = rows.isEmpty() ? 0 : seq.applyAsLong(rows.getLast());
        return new Page<>(rows, truncated, lastSeq);
    }

    @Transactional
    public SyncPullDto push(
            UUID userId,
            List<SyncCopyDto> incoming,
            List<SyncWishDto> incomingWishes,
            List<SyncPhotoDto> incomingPhotos) {
        Pushed<SyncCopyDto> copies = pushCopies(userId, incoming);
        Pushed<SyncWishDto> wishes = pushWishes(userId, incomingWishes);
        Pushed<SyncPhotoDto> photos = pushPhotos(userId, incomingPhotos);
        long cursor = Math.max(copies.highWaterMark(), Math.max(wishes.highWaterMark(), photos.highWaterMark()));
        return new SyncPullDto(copies.records(), wishes.records(), photos.records(), cursor, false);
    }

    private Pushed<SyncPhotoDto> pushPhotos(UUID userId, List<SyncPhotoDto> incoming) {
        if (incoming.isEmpty()) {
            return new Pushed<>(List.of(), 0);
        }

        List<UUID> ids = incoming.stream().map(photo -> UUID.fromString(photo.id())).toList();
        Map<UUID, PhotoEntity> stored = new HashMap<>();
        for (PhotoEntity entity : photoRepository.findAllByUserIdAndIdIn(userId, ids)) {
            stored.put(entity.getId(), entity);
        }

        List<SyncPhotoDto> results = new ArrayList<>(incoming.size());
        long highWaterMark = 0;
        for (SyncPhotoDto client : incoming) {
            UUID id = UUID.fromString(client.id());
            PhotoEntity entity = stored.get(id);
            SyncPhotoDto merged = PhotoMerge.merge(entity == null ? null : toPhotoDto(entity), client);

            PhotoEntity target = entity;
            if (target == null) {
                target = new PhotoEntity();
                target.setId(id);
                target.setUserId(userId);
            }
            applyPhoto(target, merged);
            target.setSyncSeq(copyRepository.nextSyncSeq());
            photoRepository.save(target);

            // A deleted photo's bytes are no longer anybody's: remove the object rather
            // than paying to store a picture no client will ever ask for again.
            if (merged.deletedAt() != null && merged.storageKey() != null) {
                storageService.delete(merged.storageKey());
            }

            highWaterMark = Math.max(highWaterMark, target.getSyncSeq());
            results.add(merged);
        }

        log.debug("Merged {} photos for user {}", results.size(), userId);
        return new Pushed<>(results, highWaterMark);
    }

    private void applyPhoto(PhotoEntity entity, SyncPhotoDto dto) {
        entity.setCopyId(UUID.fromString(dto.copyId()));
        entity.setStorageKey(dto.storageKey());
        entity.setContentType(dto.contentType() == null ? "application/octet-stream" : dto.contentType());
        entity.setByteSize(dto.byteSize() == null ? 0L : dto.byteSize());
        entity.setSortIndex(dto.sortIndex() == null ? 0 : dto.sortIndex());
        entity.setCreatedAt(dto.createdAt());
        entity.setDeletedAt(dto.deletedAt());
        entity.setFieldClocks(writeClocks(dto.fieldClocks()));
    }

    private SyncPhotoDto toPhotoDto(PhotoEntity entity) {
        return new SyncPhotoDto(
                entity.getId().toString(),
                entity.getCopyId().toString(),
                entity.getStorageKey(),
                entity.getContentType(),
                entity.getByteSize(),
                entity.getSortIndex(),
                entity.getCreatedAt(),
                entity.getDeletedAt(),
                readClocks(entity.getFieldClocks()));
    }

    /**
     * The merged records plus the highest sequence written.
     *
     * Returned rather than kept in a field: this service is a singleton, so per-request
     * state on the instance would be shared by every concurrent request.
     */
    private record Pushed<T>(List<T> records, long highWaterMark) {}

    private Pushed<SyncCopyDto> pushCopies(UUID userId, List<SyncCopyDto> incoming) {
        if (incoming.isEmpty()) {
            return new Pushed<>(List.of(), 0);
        }

        List<UUID> ids = incoming.stream().map(copy -> UUID.fromString(copy.id())).toList();
        Map<UUID, CopyEntity> stored = new HashMap<>();
        for (CopyEntity entity : copyRepository.findAllByUserIdAndIdIn(userId, ids)) {
            stored.put(entity.getId(), entity);
        }

        List<SyncCopyDto> results = new ArrayList<>(incoming.size());
        long highWaterMark = 0;

        for (SyncCopyDto client : incoming) {
            UUID id = UUID.fromString(client.id());
            CopyEntity entity = stored.get(id);
            SyncCopyDto merged = CopyMerge.merge(entity == null ? null : toDto(entity), client);

            CopyEntity target = entity == null ? newEntity(id, userId) : entity;
            apply(target, merged);
            // A fresh sequence on every write, whether or not the merge changed anything —
            // a client that pushed must be able to see its own push come back on the next
            // pull, or it would loop trying to resend.
            target.setSyncSeq(copyRepository.nextSyncSeq());
            copyRepository.save(target);

            highWaterMark = Math.max(highWaterMark, target.getSyncSeq());
            results.add(merged);
        }

        log.debug("Merged {} copies for user {}", results.size(), userId);
        return new Pushed<>(results, highWaterMark);
    }

    private Pushed<SyncWishDto> pushWishes(UUID userId, List<SyncWishDto> incoming) {
        if (incoming.isEmpty()) {
            return new Pushed<>(List.of(), 0);
        }

        List<UUID> ids = incoming.stream().map(wish -> UUID.fromString(wish.id())).toList();
        Map<UUID, WishlistItemEntity> stored = new HashMap<>();
        for (WishlistItemEntity entity : wishlistItemRepository.findAllByUserIdAndIdIn(userId, ids)) {
            stored.put(entity.getId(), entity);
        }

        List<SyncWishDto> results = new ArrayList<>(incoming.size());
        long highWaterMark = 0;
        for (SyncWishDto client : incoming) {
            UUID id = UUID.fromString(client.id());
            WishlistItemEntity entity = stored.get(id);
            SyncWishDto merged = WishMerge.merge(entity == null ? null : toWishDto(entity), client);

            WishlistItemEntity target = entity;
            if (target == null) {
                target = new WishlistItemEntity();
                target.setId(id);
                target.setUserId(userId);
            }
            applyWish(target, merged);
            target.setSyncSeq(copyRepository.nextSyncSeq());
            wishlistItemRepository.save(target);

            highWaterMark = Math.max(highWaterMark, target.getSyncSeq());
            results.add(merged);
        }

        log.debug("Merged {} wishes for user {}", results.size(), userId);
        return new Pushed<>(results, highWaterMark);
    }

    private void applyWish(WishlistItemEntity entity, SyncWishDto dto) {
        entity.setAlbumId(dto.albumId());
        entity.setTitle(dto.title() == null ? "Untitled" : dto.title());
        entity.setArtistName(dto.artistName() == null ? "Unknown artist" : dto.artistName());
        entity.setYear(dto.year());
        entity.setDesiredFormat(dto.desiredFormat());
        entity.setNote(dto.note());
        entity.setCreatedAt(dto.createdAt());
        entity.setDeletedAt(dto.deletedAt());
        entity.setFieldClocks(writeClocks(dto.fieldClocks()));
    }

    private SyncWishDto toWishDto(WishlistItemEntity entity) {
        return new SyncWishDto(
                entity.getId().toString(),
                entity.getAlbumId(),
                entity.getTitle(),
                entity.getArtistName(),
                entity.getYear(),
                entity.getDesiredFormat(),
                entity.getNote(),
                entity.getCreatedAt(),
                entity.getDeletedAt(),
                readClocks(entity.getFieldClocks()));
    }

    private CopyEntity newEntity(UUID id, UUID userId) {
        CopyEntity entity = new CopyEntity();
        entity.setId(id);
        entity.setUserId(userId);
        return entity;
    }

    private void apply(CopyEntity entity, SyncCopyDto dto) {
        entity.setReleaseId(dto.releaseId());
        entity.setManualTitle(dto.manualTitle());
        entity.setManualArtist(dto.manualArtist());
        entity.setManualYear(dto.manualYear());
        entity.setManualLabel(dto.manualLabel());
        entity.setManualCatalogNumber(dto.manualCatalogNumber());
        entity.setManualFormat(dto.manualFormat());
        entity.setCondition(dto.condition());
        entity.setSleeveCondition(dto.sleeveCondition());
        // Absent means a client older than the field, which is the same as having said nothing.
        entity.setCatalogArt(dto.catalogArt() == null ? "AUTO" : dto.catalogArt());
        entity.setPricePaidCents(dto.pricePaidCents());
        entity.setCurrency(dto.currency() == null ? "EUR" : dto.currency());
        entity.setPurchasedOn(dto.purchasedOn());
        entity.setPurchasedAt(dto.purchasedAt());
        entity.setNotes(dto.notes());
        entity.setNotesConflict(dto.notesConflict());
        entity.setRating(dto.rating());
        // Absent means a client older than the field, which is the same as not hidden.
        entity.setHidden(Boolean.TRUE.equals(dto.hidden()));
        entity.setCreatedAt(dto.createdAt());
        entity.setDeletedAt(dto.deletedAt());
        entity.setFieldClocks(writeClocks(dto.fieldClocks()));
    }

    private SyncCopyDto toDto(CopyEntity entity) {
        return new SyncCopyDto(
                entity.getId().toString(),
                entity.getReleaseId(),
                entity.getManualTitle(),
                entity.getManualArtist(),
                entity.getManualYear(),
                entity.getManualLabel(),
                entity.getManualCatalogNumber(),
                entity.getManualFormat(),
                entity.getCondition(),
                entity.getSleeveCondition(),
                entity.getCatalogArt(),
                entity.getPricePaidCents(),
                entity.getCurrency(),
                entity.getPurchasedOn(),
                entity.getPurchasedAt(),
                entity.getNotes(),
                entity.getNotesConflict(),
                entity.getRating(),
                entity.isHidden(),
                entity.getCreatedAt(),
                entity.getDeletedAt(),
                readClocks(entity.getFieldClocks()));
    }

    // Spring Boot 4.1 auto-configures Jackson 3 (tools.jackson), not the 2.x ObjectMapper.
    // Both are on the classpath, so injecting the wrong one fails at startup rather than at
    // runtime -- which is how this was found.
    private String writeClocks(Map<String, String> clocks) {
        return objectMapper.writeValueAsString(clocks == null ? Map.of() : clocks);
    }

    private Map<String, String> readClocks(String json) {
        return objectMapper.readValue(json, CLOCKS);
    }
}
