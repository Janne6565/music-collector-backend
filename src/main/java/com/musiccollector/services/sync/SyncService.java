package com.musiccollector.services.sync;

import com.musiccollector.entity.CopyEntity;
import com.musiccollector.model.core.SyncCopyDto;
import com.musiccollector.model.core.SyncPullDto;
import com.musiccollector.repository.CopyRepository;
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
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SyncPullDto pull(UUID userId, long since) {
        List<CopyEntity> changed =
                copyRepository.findAllByUserIdAndSyncSeqGreaterThanOrderBySyncSeqAsc(userId, since);

        boolean hasMore = changed.size() > PULL_PAGE_SIZE;
        List<CopyEntity> page = hasMore ? changed.subList(0, PULL_PAGE_SIZE) : changed;

        long cursor = page.isEmpty() ? since : page.getLast().getSyncSeq();
        return new SyncPullDto(page.stream().map(this::toDto).toList(), cursor, hasMore);
    }

    /**
     * Merges a batch of client records into the user's collection and returns the merged
     * results, so the client can adopt whatever the server decided.
     */
    @Transactional
    public SyncPullDto push(UUID userId, List<SyncCopyDto> incoming) {
        if (incoming.isEmpty()) {
            return new SyncPullDto(List.of(), 0, false);
        }

        List<UUID> ids = incoming.stream().map(copy -> UUID.fromString(copy.id())).toList();
        Map<UUID, CopyEntity> stored = new HashMap<>();
        for (CopyEntity entity : copyRepository.findAllByUserIdAndIdIn(userId, ids)) {
            stored.put(entity.getId(), entity);
        }

        List<SyncCopyDto> results = new ArrayList<>(incoming.size());
        long cursor = 0;

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

            cursor = Math.max(cursor, target.getSyncSeq());
            results.add(merged);
        }

        log.debug("Merged {} copies for user {}", results.size(), userId);
        return new SyncPullDto(results, cursor, false);
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
