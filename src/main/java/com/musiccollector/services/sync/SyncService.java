package com.musiccollector.services.sync;

import com.musiccollector.entity.CopyEntity;
import com.musiccollector.entity.WishlistItemEntity;
import com.musiccollector.model.core.SyncCopyDto;
import com.musiccollector.model.core.SyncPullDto;
import com.musiccollector.model.core.SyncWishDto;
import com.musiccollector.repository.CopyRepository;
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
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SyncPullDto pull(UUID userId, long since) {
        List<CopyEntity> changedCopies =
                copyRepository.findAllByUserIdAndSyncSeqGreaterThanOrderBySyncSeqAsc(userId, since);
        List<WishlistItemEntity> changedWishes =
                wishlistItemRepository.findAllByUserIdAndSyncSeqGreaterThanOrderBySyncSeqAsc(userId, since);

        boolean hasMore = changedCopies.size() > PULL_PAGE_SIZE || changedWishes.size() > PULL_PAGE_SIZE;
        List<CopyEntity> copyPage =
                changedCopies.size() > PULL_PAGE_SIZE ? changedCopies.subList(0, PULL_PAGE_SIZE) : changedCopies;
        List<WishlistItemEntity> wishPage =
                changedWishes.size() > PULL_PAGE_SIZE ? changedWishes.subList(0, PULL_PAGE_SIZE) : changedWishes;

        // The cursor is the lowest of the two high-water marks, so a page that truncated one
        // kind cannot advance past records of the other kind that were never sent.
        long cursor = since;
        if (!copyPage.isEmpty() || !wishPage.isEmpty()) {
            long copyMax = copyPage.isEmpty() ? Long.MAX_VALUE : copyPage.getLast().getSyncSeq();
            long wishMax = wishPage.isEmpty() ? Long.MAX_VALUE : wishPage.getLast().getSyncSeq();
            cursor = Math.min(copyMax, wishMax);
        }

        final long limit = cursor;
        return new SyncPullDto(
                copyPage.stream().filter(copy -> copy.getSyncSeq() <= limit).map(this::toDto).toList(),
                wishPage.stream().filter(wish -> wish.getSyncSeq() <= limit).map(this::toWishDto).toList(),
                limit,
                hasMore);
    }

    /**
     * Merges a batch of client records into the user's collection and returns the merged
     * results, so the client can adopt whatever the server decided.
     */
    @Transactional
    public SyncPullDto push(UUID userId, List<SyncCopyDto> incoming, List<SyncWishDto> incomingWishes) {
        Pushed<SyncCopyDto> copies = pushCopies(userId, incoming);
        Pushed<SyncWishDto> wishes = pushWishes(userId, incomingWishes);
        return new SyncPullDto(
                copies.records(), wishes.records(), Math.max(copies.highWaterMark(), wishes.highWaterMark()), false);
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
        entity.setReleaseGroupMbid(dto.releaseGroupMbid());
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
                entity.getReleaseGroupMbid(),
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
        entity.setReleaseMbid(dto.releaseMbid());
        entity.setCondition(dto.condition());
        entity.setPricePaidCents(dto.pricePaidCents());
        entity.setCurrency(dto.currency() == null ? "EUR" : dto.currency());
        entity.setPurchasedOn(dto.purchasedOn());
        entity.setPurchasedAt(dto.purchasedAt());
        entity.setNotes(dto.notes());
        entity.setNotesConflict(dto.notesConflict());
        entity.setRating(dto.rating());
        entity.setCreatedAt(dto.createdAt());
        entity.setDeletedAt(dto.deletedAt());
        entity.setFieldClocks(writeClocks(dto.fieldClocks()));
    }

    private SyncCopyDto toDto(CopyEntity entity) {
        return new SyncCopyDto(
                entity.getId().toString(),
                entity.getReleaseMbid(),
                entity.getCondition(),
                entity.getPricePaidCents(),
                entity.getCurrency(),
                entity.getPurchasedOn(),
                entity.getPurchasedAt(),
                entity.getNotes(),
                entity.getNotesConflict(),
                entity.getRating(),
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
