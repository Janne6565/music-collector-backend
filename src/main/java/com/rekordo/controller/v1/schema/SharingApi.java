package com.rekordo.controller.v1.schema;

import com.rekordo.model.action.UpdateSharingRequest;
import com.rekordo.model.core.SharingSettingsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** Who may see the collection, who may see the wishlist, and whether prices ride along. */
@RequestMapping("/api/v1/sharing")
@Tag(name = "Sharing")
public interface SharingApi {

    @GetMapping
    @Operation(summary = "The current sharing settings")
    @ApiResponse(responseCode = "200", description = "The settings, defaults included for an account that has never saved any")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<SharingSettingsDto> read();

    @PutMapping
    @Operation(
            summary = "Replace the sharing settings",
            description = "Every field is required: on a privacy screen, \"leave this alone\" and \"set it to the default\" must not look the same.")
    @ApiResponse(responseCode = "200", description = "Saved")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<SharingSettingsDto> update(@Valid @RequestBody UpdateSharingRequest request);
}
