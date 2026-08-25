package com.musiccollector.model.core;

import com.fasterxml.jackson.annotation.JsonAlias;

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
        /**
         * Source-qualified, and accepted under its old name too.
         *
         * A client deployed before the rename still pushes `releaseMbid`, and reading that
         * as absent would detach the copy from its release — silently, in a sync batch.
         * The alias makes the order of the two deploys stop mattering. It can go once no
         * client of that vintage is left.
         */
        @JsonAlias("releaseMbid") String releaseId,
        /** The media grade, on the Goldmine scale. */
        String condition,
        /** The sleeve grade, graded separately from the media. */
        String sleeveCondition,
        /**
         * Show the release's own artwork instead of the copy's first photo.
         *
         * Boxed, because a client older than this field sends nothing at all and that has
         * to stay distinguishable from a deliberate {@code false} while it travels. It is
         * read as {@code false} the moment it lands — see {@code SyncService.apply}.
         */
        Boolean preferCatalogArt,
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
