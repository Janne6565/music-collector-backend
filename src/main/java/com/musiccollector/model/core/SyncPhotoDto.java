package com.musiccollector.model.core;

import java.util.Map;

/**
 * A sleeve photo's metadata, as it travels between a client and the server.
 *
 * The bytes are not in here: they move over the dedicated upload and download endpoints.
 * Putting a multi-megabyte image inside a sync batch would make every sync as slow as the
 * largest photo in it.
 */
public record SyncPhotoDto(
        String id,
        String copyId,
        String storageKey,
        String contentType,
        Long byteSize,
        Integer sortIndex,
        Long createdAt,
        Long deletedAt,
        Map<String, String> fieldClocks) {}
