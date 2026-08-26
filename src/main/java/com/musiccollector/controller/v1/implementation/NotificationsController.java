package com.musiccollector.controller.v1.implementation;

import com.musiccollector.controller.v1.schema.NotificationsApi;
import com.musiccollector.model.action.UpdateNotificationPreferenceRequest;
import com.musiccollector.model.core.NotificationPreferencesDto;
import com.musiccollector.security.CurrentUser;
import com.musiccollector.services.notifications.NotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NotificationsController implements NotificationsApi {

    private final NotificationPreferenceService preferenceService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<NotificationPreferencesDto> preferences() {
        return ResponseEntity.ok(preferenceService.forUser(currentUser.require()));
    }

    @Override
    public ResponseEntity<NotificationPreferencesDto> updatePreference(UpdateNotificationPreferenceRequest request) {
        return ResponseEntity.ok(preferenceService.update(
                currentUser.require(), request.category(), request.mail(), request.push()));
    }
}
