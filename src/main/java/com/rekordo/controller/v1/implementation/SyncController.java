package com.rekordo.controller.v1.implementation;

import com.rekordo.controller.v1.schema.SyncApi;
import com.rekordo.model.action.SyncPushRequest;
import com.rekordo.model.core.SyncPullDto;
import com.rekordo.security.CurrentUser;
import com.rekordo.services.sync.SyncService;
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
                syncService.push(
                        currentUser.require().getId(),
                        request.safeCopies(),
                        request.safeWishes(),
                        request.safePhotos(),
                        request.safeReleases(),
                        request.origins()));
    }
}
