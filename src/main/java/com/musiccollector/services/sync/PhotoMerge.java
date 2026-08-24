package com.musiccollector.services.sync;

import com.musiccollector.model.core.SyncPhotoDto;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The same field-level rule as {@link CopyMerge}, applied to a photo's metadata.
 *
 * Only two fields are really mutable — where it sits in the strip and whether it has been
 * deleted. The bytes themselves are immutable: a photo id points at one object forever, so
 * `storageKey`, `contentType` and `byteSize` never legitimately change, and merging them
 * is only about agreeing which device's upload record won.
 */
public final class PhotoMerge {

    public static final List<String> MERGEABLE_FIELDS =
            List.of("copyId", "storageKey", "contentType", "byteSize", "sortIndex", "deletedAt");

    private PhotoMerge() {}

    public static SyncPhotoDto merge(SyncPhotoDto local, SyncPhotoDto remote) {
        if (local == null && remote == null) {
            throw new IllegalArgumentException("merge needs at least one side");
        }
        if (local == null) {
            return remote;
        }
        if (remote == null) {
            return local;
        }
        if (!Objects.equals(local.id(), remote.id())) {
            throw new IllegalArgumentException(
                    "merge got two different photos: " + local.id() + " vs " + remote.id());
        }

        Map<String, String> clocks = new LinkedHashMap<>();
        Map<String, Object> values = new java.util.HashMap<>();

        for (String field : MERGEABLE_FIELDS) {
            String localClock = clockOf(local, field);
            String remoteClock = clockOf(remote, field);
            boolean remoteWins = remoteWins(localClock, remoteClock);
            values.put(field, valueOf(remoteWins ? remote : local, field));
            clocks.put(field, remoteWins ? remoteClock : localClock);
        }

        return new SyncPhotoDto(
                local.id(),
                (String) values.get("copyId"),
                (String) values.get("storageKey"),
                (String) values.get("contentType"),
                (Long) values.get("byteSize"),
                (Integer) values.get("sortIndex"),
                Math.min(local.createdAt(), remote.createdAt()),
                (Long) values.get("deletedAt"),
                clocks);
    }

    private static boolean remoteWins(String localClock, String remoteClock) {
        if (remoteClock == null) {
            return false;
        }
        if (localClock == null) {
            return true;
        }
        return remoteClock.compareTo(localClock) > 0;
    }

    private static String clockOf(SyncPhotoDto photo, String field) {
        Map<String, String> clocks = photo.fieldClocks();
        return clocks == null ? null : clocks.get(field);
    }

    private static Object valueOf(SyncPhotoDto photo, String field) {
        return switch (field) {
            case "copyId" -> photo.copyId();
            case "storageKey" -> photo.storageKey();
            case "contentType" -> photo.contentType();
            case "byteSize" -> photo.byteSize();
            case "sortIndex" -> photo.sortIndex();
            case "deletedAt" -> photo.deletedAt();
            default -> throw new IllegalArgumentException("Not a mergeable field: " + field);
        };
    }

    public static List<SyncPhotoDto> mergeAll(Collection<SyncPhotoDto> local, Collection<SyncPhotoDto> remote) {
        Map<String, SyncPhotoDto[]> byId = new LinkedHashMap<>();
        for (SyncPhotoDto photo : local) {
            byId.computeIfAbsent(photo.id(), unused -> new SyncPhotoDto[2])[0] = photo;
        }
        for (SyncPhotoDto photo : remote) {
            byId.computeIfAbsent(photo.id(), unused -> new SyncPhotoDto[2])[1] = photo;
        }
        return byId.values().stream().map(pair -> merge(pair[0], pair[1])).toList();
    }
}
