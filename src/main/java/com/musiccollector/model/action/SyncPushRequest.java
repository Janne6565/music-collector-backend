package com.musiccollector.model.action;

import com.musiccollector.model.core.SyncCopyDto;
import com.musiccollector.model.core.SyncWishDto;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Copies and wishes push together, so a device that added a record and wished for another
 * in the same offline session sends one request rather than racing two.
 *
 * Both lists are optional; a client with nothing of one kind simply omits it.
 */
public record SyncPushRequest(
        @Size(max = 500, message = "Push at most 500 copies per request") List<SyncCopyDto> copies,
        @Size(max = 500, message = "Push at most 500 wishes per request") List<SyncWishDto> wishes) {

    public List<SyncCopyDto> safeCopies() {
        return copies == null ? List.of() : copies;
    }

    public List<SyncWishDto> safeWishes() {
        return wishes == null ? List.of() : wishes;
    }
}
