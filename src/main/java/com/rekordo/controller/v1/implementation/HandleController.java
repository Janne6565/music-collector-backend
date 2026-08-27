package com.rekordo.controller.v1.implementation;

import com.rekordo.controller.v1.schema.HandleApi;
import com.rekordo.model.action.ClaimHandleRequest;
import com.rekordo.model.core.HandleAvailabilityDto;
import com.rekordo.model.core.SharingSettingsDto;
import com.rekordo.security.CurrentUser;
import com.rekordo.services.social.HandleService;
import com.rekordo.services.social.SharingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class HandleController implements HandleApi {

    private final HandleService handleService;
    private final SharingService sharingService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<HandleAvailabilityDto> availability(String handle) {
        return ResponseEntity.ok(handleService.check(currentUser.require().getId(), handle));
    }

    @Override
    public ResponseEntity<SharingSettingsDto> claim(ClaimHandleRequest request) {
        UUID userId = currentUser.require().getId();
        handleService.claim(userId, request.handle());
        // The claim screen's next stop is the Friends tab, which needs the settings anyway.
        return ResponseEntity.ok(sharingService.read(userId));
    }
}
