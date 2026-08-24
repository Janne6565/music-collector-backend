package com.musiccollector.model.core;

import java.util.Map;

/**
 * One wishlist entry, as it travels between a client and the server. Field names and types
 * match the clients' local records exactly — the merge is a shared contract.
 */
public record SyncWishDto(
        String id,
        String releaseGroupMbid,
        String title,
        String artistName,
        Integer year,
        String desiredFormat,
        String note,
        Long createdAt,
        Long deletedAt,
        Map<String, String> fieldClocks) {}
