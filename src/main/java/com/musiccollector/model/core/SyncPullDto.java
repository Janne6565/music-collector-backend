package com.musiccollector.model.core;

import java.util.List;

/**
 * @param copies  everything changed since the client's cursor, tombstones included
 * @param cursor  what to send as {@code since} next time
 * @param hasMore whether another page is waiting; the client should pull again immediately
 */
public record SyncPullDto(List<SyncCopyDto> copies, long cursor, boolean hasMore) {}
