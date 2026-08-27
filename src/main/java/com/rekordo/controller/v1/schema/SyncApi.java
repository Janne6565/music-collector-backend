package com.rekordo.controller.v1.schema;

import com.rekordo.model.action.SyncPushRequest;
import com.rekordo.model.core.SyncPullDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Cross-device sync for a signed-in account.
 *
 * There are no CRUD endpoints for copies anywhere in this API — clients are local-first and
 * read their library from their own store. These two endpoints are the only way collection
 * data reaches the server, and the server participates by merging rather than overwriting.
 */
@RequestMapping("/api/v1/sync")
@Tag(name = "Sync")
public interface SyncApi {

    @GetMapping
    @Operation(
            summary = "Everything changed since the client's cursor",
            description = "Includes tombstones, so deletes propagate. Poll again while hasMore is true.")
    @ApiResponse(responseCode = "200", description = "Changed copies and the next cursor")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<SyncPullDto> pull(
            @RequestParam(name = "since", defaultValue = "0") @Min(0) long since);

    @PostMapping
    @Operation(
            summary = "Push local changes and take back the merged result",
            description = "Each copy is merged against what the server holds, using the shared contract.")
    @ApiResponse(responseCode = "200", description = "The merged copies and the new cursor")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<SyncPullDto> push(@Valid @RequestBody SyncPushRequest request);
}
