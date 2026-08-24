package com.musiccollector.controller.v1.implementation;

import com.musiccollector.controller.v1.schema.SyncApi;
import com.musiccollector.model.action.SyncPushRequest;
import com.musiccollector.model.core.SyncPullDto;
import com.musiccollector.security.CurrentUser;
import com.musiccollector.services.sync.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
public class SyncController implements SyncApi {

    private final SyncService syncService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<SyncPullDto> pull(long since) {
        return ResponseEntity.ok(syncService.pull(currentUser.require().getId(), since));
    }

    @Override
    public ResponseEntity<SyncPullDto> push(SyncPushRequest request) {
        return ResponseEntity.ok(
                syncService.push(currentUser.require().getId(), request.safeCopies(), request.safeWishes()));
    }
}
