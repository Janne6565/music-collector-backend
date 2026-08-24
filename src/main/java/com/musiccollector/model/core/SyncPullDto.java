package com.musiccollector.model.core;

import java.util.List;

/**
 * @param copies   changed copies since the client's cursor, tombstones included
 * @param wishes   changed wishlist entries, likewise
 * @param photos   changed photo metadata; the bytes move over the photo endpoints
 * @param cursor   what to send as {@code since} next time
 * @param hasMore  whether another page is waiting; the client should pull again immediately
 */
public record SyncPullDto(
        List<SyncCopyDto> copies,
        List<SyncWishDto> wishes,
        List<SyncPhotoDto> photos,
        long cursor,
        boolean hasMore) {}
