package com.musiccollector.model.core;

import java.util.Map;

/**
 * One user-owned copy, as it travels between a client and the server.
 *
 * Field names and types match the clients' local records exactly — the merge is a shared
 * contract, so a rename here without the matching rename there would silently change which
 * side wins a conflict.
 */
public record SyncCopyDto(
        String id,
        String releaseMbid,
        String condition,
        Integer pricePaidCents,
        String currency,
        String purchasedOn,
        String purchasedAt,
        String notes,
        /** The other version of the notes this record knows about; derived by the merge. */
        String notesConflict,
        Integer rating,
        Long createdAt,
        Long deletedAt,
        /** Encoded HLC per mergeable field: {@code wall:counter:node}, fixed width. */
        Map<String, String> fieldClocks) {}
