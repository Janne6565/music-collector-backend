package com.rekordo.model.core;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.Map;

/**
 * One wishlist entry, as it travels between a client and the server. Field names and types
 * match the clients' local records exactly — the merge is a shared contract.
 */
public record SyncWishDto(
        String id,
        /** Accepted as `releaseGroupMbid` too, for the same reason as SyncCopyDto#releaseId. */
        @JsonAlias("releaseGroupMbid") String albumId,
        /** The pressing the entry was made from, or null. Absent from an older client. */
        String releaseId,
        /** Mirrors {@code SyncCopyDto#pendingBarcode}. */
        String pendingBarcode,
        String title,
        String artistName,
        Integer year,
        String desiredFormat,
        String note,
        Integer sortIndex,
        Long createdAt,
        Long deletedAt,
        Map<String, String> fieldClocks) {}
