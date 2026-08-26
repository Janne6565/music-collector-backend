package com.musiccollector.model.action;

import com.musiccollector.model.core.ReleaseDto;
import com.musiccollector.model.core.SyncCopyDto;
import com.musiccollector.model.core.SyncPhotoDto;
import com.musiccollector.model.core.SyncWishDto;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Copies, wishes and photo metadata push together, so an offline session that touched
 * several kinds sends one request rather than racing three.
 *
 * All three lists are optional; a client with nothing of a kind simply omits it.
 */
public record SyncPushRequest(
        @Size(max = 500, message = "Push at most 500 copies per request") List<SyncCopyDto> copies,
        @Size(max = 500, message = "Push at most 500 wishes per request") List<SyncWishDto> wishes,
        @Size(max = 500, message = "Push at most 500 photos per request") List<SyncPhotoDto> photos,
        /**
         * The catalogue rows behind the copies in this push, as the device holds them.
         *
         * Sync does not move the catalogue -- a release is a shared cache, not somebody's
         * record -- but the mirror only ever learns about one when a client looks it up
         * through the metadata proxy. A collection that arrived any other way therefore
         * names releases the server cannot resolve, and every *other* device is left with a
         * shelf of untitled placeholders it has no way to fill. For a Discogs id it stays
         * that way for good: there is no lookup by id for those at all.
         *
         * So the device that has them hands them over. Optional, and ignored where the
         * mirror already holds the row -- what it fetched itself is better than an echo.
         */
        @Size(max = 500, message = "Push at most 500 releases per request") List<ReleaseDto> releases,
        /**
         * Why each copy in this batch exists — {@code MANUAL}, {@code CSV_IMPORT} or
         * {@code FIRST_SYNC}, keyed by copy id.
         *
         * Beside the records rather than on them, because it is not a property of the copy
         * that has to survive or merge: it is the reason for this particular push, and it
         * matters exactly once, when the server first sees the row. Only the device can
         * answer it — an import of two hundred and two hundred records typed in over a
         * fortnight arrive here in the same shape.
         *
         * A copy the map does not mention is silent. A client too old to send this is one
         * whose intent we cannot read, and the safe failure mode for somebody's feed is to
         * say nothing rather than to announce their whole collection.
         */
        Map<String, String> origins) {

    public String originOf(String copyId) {
        return origins == null ? null : origins.get(copyId);
    }

    public List<SyncCopyDto> safeCopies() {
        return copies == null ? List.of() : copies;
    }

    public List<SyncWishDto> safeWishes() {
        return wishes == null ? List.of() : wishes;
    }

    public List<SyncPhotoDto> safePhotos() {
        return photos == null ? List.of() : photos;
    }

    public List<ReleaseDto> safeReleases() {
        return releases == null ? List.of() : releases;
    }
}
