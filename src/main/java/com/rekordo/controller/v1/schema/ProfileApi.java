package com.rekordo.controller.v1.schema;

import com.rekordo.model.core.ProfileDto;
import com.rekordo.model.core.ProfileSummaryDto;
import com.rekordo.model.core.SharedCollectionDto;
import com.rekordo.model.core.SharedWishlistDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Other people's shelves.
 *
 * <p>Open without an account, like the metadata proxy above it. Someone handed a handle
 * should be able to look before deciding the app is worth signing up for, and a public
 * shelf that demands a login to read is not public. What the viewer is allowed to see still
 * depends entirely on who they turn out to be — every endpoint here answers differently for
 * the owner, a friend and a stranger.
 */
@RequestMapping("/api/v1/profiles")
@Tag(name = "Profiles")
public interface ProfileApi {

    @GetMapping
    @Operation(
            summary = "Find a collector by handle",
            description = "Prefix match on the handle only, never on anybody's name. Three characters minimum; a shorter query returns nothing rather than the start of the directory.")
    @ApiResponse(responseCode = "200", description = "Up to twenty matches, best first")
    @ApiResponse(responseCode = "429", description = "Too many searches from this address")
    ResponseEntity<List<ProfileSummaryDto>> searchProfiles(@RequestParam("q") String query);

    @GetMapping("/{handle}")
    @Operation(
            summary = "One collector's profile",
            description = "Resolves even when every list behind it is closed — the locked shelf is a screen the design draws, with a name on it and a way to ask.")
    @ApiResponse(responseCode = "200", description = "The profile, with this viewer's verdicts on it")
    @ApiResponse(responseCode = "404", description = "No collector goes by that handle")
    ResponseEntity<ProfileDto> profile(@PathVariable String handle);

    @GetMapping("/{handle}/collection")
    @Operation(summary = "Their collection, as this viewer is allowed to see it")
    @ApiResponse(responseCode = "200", description = "The shelf")
    @ApiResponse(responseCode = "403", description = "They do not share the collection with you")
    @ApiResponse(responseCode = "404", description = "No collector goes by that handle")
    ResponseEntity<SharedCollectionDto> collection(@PathVariable String handle);

    @GetMapping("/{handle}/wishlist")
    @Operation(summary = "Their wishlist, as this viewer is allowed to see it")
    @ApiResponse(responseCode = "200", description = "The wishlist")
    @ApiResponse(responseCode = "403", description = "They do not share the wishlist with you")
    @ApiResponse(responseCode = "404", description = "No collector goes by that handle")
    ResponseEntity<SharedWishlistDto> wishlist(@PathVariable String handle);
}
