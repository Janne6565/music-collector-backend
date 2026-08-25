package com.musiccollector.controller.v1.implementation;

import com.musiccollector.controller.v1.schema.SharingApi;
import com.musiccollector.model.action.UpdateSharingRequest;
import com.musiccollector.model.core.SharingSettingsDto;
import com.musiccollector.security.CurrentUser;
import com.musiccollector.services.social.SharingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SharingController implements SharingApi {

    private final SharingService sharingService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<SharingSettingsDto> read() {
        return ResponseEntity.ok(sharingService.read(currentUser.require().getId()));
    }

    @Override
    public ResponseEntity<SharingSettingsDto> update(UpdateSharingRequest request) {
        return ResponseEntity.ok(sharingService.update(currentUser.require().getId(), request));
    }
}
