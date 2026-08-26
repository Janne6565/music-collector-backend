package com.musiccollector.services.sync;

import com.musiccollector.model.core.SyncWishDto;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The same field-level rule as {@link CopyMerge}, applied to a wishlist entry.
 *
 * No special case for the note: unlike a copy's notes, a wish note is a one-line reminder
 * ("MOFI or Japanese pressing"), not a paragraph worth preserving both halves of.
 */
public final class WishMerge {

    public static final List<String> MERGEABLE_FIELDS = List.of(
            "albumId",
            "releaseId",
            "title",
            "artistName",
            "year",
            "desiredFormat",
            "note",
            "sortIndex",
            "deletedAt");

    private WishMerge() {}

    public static SyncWishDto merge(SyncWishDto local, SyncWishDto remote) {
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
                    "merge got two different wishes: " + local.id() + " vs " + remote.id());
        }

        Map<String, String> clocks = new LinkedHashMap<>();
        Map<String, Object> values = new HashMap<>();

        for (String field : MERGEABLE_FIELDS) {
            String localClock = clockOf(local, field);
            String remoteClock = clockOf(remote, field);
            boolean remoteWins = remoteWins(localClock, remoteClock);
            values.put(field, valueOf(remoteWins ? remote : local, field));
            clocks.put(field, remoteWins ? remoteClock : localClock);
        }

        return new SyncWishDto(
                local.id(),
                (String) values.get("albumId"),
                (String) values.get("releaseId"),
                (String) values.get("title"),
                (String) values.get("artistName"),
                (Integer) values.get("year"),
                (String) values.get("desiredFormat"),
                (String) values.get("note"),
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

    private static String clockOf(SyncWishDto wish, String field) {
        Map<String, String> clocks = wish.fieldClocks();
        return clocks == null ? null : clocks.get(field);
    }

    private static Object valueOf(SyncWishDto wish, String field) {
        return switch (field) {
            case "albumId" -> wish.albumId();
            case "releaseId" -> wish.releaseId();
            case "title" -> wish.title();
            case "artistName" -> wish.artistName();
            case "year" -> wish.year();
            case "desiredFormat" -> wish.desiredFormat();
            case "note" -> wish.note();
            case "sortIndex" -> wish.sortIndex();
            case "deletedAt" -> wish.deletedAt();
            default -> throw new IllegalArgumentException("Not a mergeable field: " + field);
        };
    }

    public static List<SyncWishDto> mergeAll(Collection<SyncWishDto> local, Collection<SyncWishDto> remote) {
        Map<String, SyncWishDto[]> byId = new LinkedHashMap<>();
        for (SyncWishDto wish : local) {
            byId.computeIfAbsent(wish.id(), unused -> new SyncWishDto[2])[0] = wish;
        }
        for (SyncWishDto wish : remote) {
            byId.computeIfAbsent(wish.id(), unused -> new SyncWishDto[2])[1] = wish;
        }
        return byId.values().stream().map(pair -> merge(pair[0], pair[1])).toList();
    }
}
