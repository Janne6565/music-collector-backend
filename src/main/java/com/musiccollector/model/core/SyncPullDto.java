package com.musiccollector.model.core;

import java.util.List;

/**
 * @param copies   changed copies since the client's cursor, tombstones included
 * @param wishes   changed wishlist entries, likewise
 * @param cursor   what to send as {@code since} next time
 * @param hasMore  whether another page is waiting; the client should pull again immediately
 */
public record SyncPullDto(
        List<SyncCopyDto> copies, List<SyncWishDto> wishes, long cursor, boolean hasMore) {}
