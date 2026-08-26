package com.musiccollector.controller.v1.schema;

import com.musiccollector.model.action.UpdateNotificationPreferenceRequest;
import com.musiccollector.model.core.NotificationPreferencesDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Notifications", description = "What may reach you outside the app")
@RequestMapping("/api/v1/notifications")
public interface NotificationsApi {

    @GetMapping("/preferences")
    @Operation(
            summary = "What may reach you, and on which channel",
            description = "Follows the account rather than the device it was set on, unlike everything "
                    + "under Settings. Every category is always present: the answer is the whole grid, "
                    + "defaults included, so no client has to know what a default is.")
    @ApiResponse(responseCode = "200", description = "The grid")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<NotificationPreferencesDto> preferences();

    @PatchMapping("/preferences")
    @Operation(
            summary = "Flip one row of the grid",
            description = "The screen saves as you go, so a request is one category rather than the "
                    + "whole grid -- and the answer is the whole grid, so nothing has to be re-derived. "
                    + "The mail switch on a locked category is ignored: a notice you can silence is not "
                    + "a notice, and that is not enforced in a client.")
    @ApiResponse(responseCode = "200", description = "The grid, as it now reads")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<NotificationPreferencesDto> updatePreference(
            @Valid @RequestBody UpdateNotificationPreferenceRequest request);
}
