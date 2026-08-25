package com.musiccollector.controller.v1.schema;

import com.musiccollector.model.action.ClaimHandleRequest;
import com.musiccollector.model.core.HandleAvailabilityDto;
import com.musiccollector.model.core.SharingSettingsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The handle other collectors find you by.
 *
 * <p>Claimed the first time Friends is opened rather than at sign-up: the app is a
 * collection tracker first, and an account that never opens Friends never needs one.
 */
@RequestMapping("/api/v1/handles")
@Tag(name = "Handles")
public interface HandleApi {

    @GetMapping("/availability")
    @Operation(
            summary = "Whether a handle can be claimed",
            description = "Answers while the field is being typed in, so the claim screen can say why before it says no.")
    @ApiResponse(responseCode = "200", description = "Available, or the reason it is not")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<HandleAvailabilityDto> availability(@RequestParam("handle") String handle);

    @PostMapping
    @Operation(
            summary = "Claim a handle, or change the one you have",
            description = "Changing is capped at twice a year; claiming the handle you already hold is free.")
    @ApiResponse(responseCode = "200", description = "Claimed")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    @ApiResponse(responseCode = "409", description = "Taken, reserved, malformed, or too many changes this year")
    ResponseEntity<SharingSettingsDto> claim(@Valid @RequestBody ClaimHandleRequest request);
}
