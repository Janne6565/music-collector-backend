package com.musiccollector.model.action;

import com.musiccollector.model.core.SyncCopyDto;
import com.musiccollector.model.core.SyncPhotoDto;
import com.musiccollector.model.core.SyncWishDto;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Copies, wishes and photo metadata push together, so an offline session that touched
 * several kinds sends one request rather than racing three.
 *
 * All three lists are optional; a client with nothing of a kind simply omits it.
 */
public record SyncPushRequest(
        @Size(max = 500, message = "Push at most 500 copies per request") List<SyncCopyDto> copies,
        @Size(max = 500, message = "Push at most 500 wishes per request") List<SyncWishDto> wishes,
        @Size(max = 500, message = "Push at most 500 photos per request") List<SyncPhotoDto> photos) {

    public List<SyncCopyDto> safeCopies() {
        return copies == null ? List.of() : copies;
    }

    public List<SyncWishDto> safeWishes() {
        return wishes == null ? List.of() : wishes;
    }

    public List<SyncPhotoDto> safePhotos() {
        return photos == null ? List.of() : photos;
    }
}
