package com.musiccollector.controller.v1.schema;

import com.musiccollector.model.action.SendFriendRequest;
import com.musiccollector.model.core.ActivityFeedDto;
import com.musiccollector.model.core.FriendsOverviewDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * The friend graph. Mutual by construction: asking creates a request, and only the person
 * asked can turn it into a friendship.
 */
@RequestMapping("/api/v1/friends")
@Tag(name = "Friends")
public interface FriendsApi {

    @GetMapping
    @Operation(
            summary = "Friends, incoming requests and outstanding asks",
            description = "One call rather than three: the Friends screen draws all of them together, and three loading states for one panel is three ways to render it half-finished.")
    @ApiResponse(responseCode = "200", description = "The People panel")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<FriendsOverviewDto> overview();

    @GetMapping("/activity")
    @Operation(
            summary = "What your friends have been doing",
            description = "Newest first. Imports never appear here, and a burst of single adds by one person collapses to one line. Visibility is applied on the way out, so a shelf that closes takes its history with it.")
    @ApiResponse(responseCode = "200", description = "The feed")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<ActivityFeedDto> activity();

    @PostMapping("/requests")
    @Operation(summary = "Ask a collector to be friends, by handle")
    @ApiResponse(responseCode = "204", description = "Asked")
    @ApiResponse(responseCode = "400", description = "That handle is your own")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    @ApiResponse(responseCode = "404", description = "No collector goes by that handle")
    @ApiResponse(responseCode = "409", description = "Already friends, or a request is already open either way")
    ResponseEntity<Void> request(@Valid @RequestBody SendFriendRequest request);

    @PostMapping("/requests/{id}/accept")
    @Operation(summary = "Accept a request that was addressed to you")
    @ApiResponse(responseCode = "204", description = "Accepted")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    @ApiResponse(responseCode = "404", description = "No pending request with that id is yours to answer")
    ResponseEntity<Void> accept(@PathVariable UUID id);

    @PostMapping("/requests/{id}/decline")
    @Operation(
            summary = "Turn a request down",
            description = "The request is deleted rather than remembered, so a mis-tap can be undone by the other person simply asking again.")
    @ApiResponse(responseCode = "204", description = "Declined")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    @ApiResponse(responseCode = "404", description = "No pending request with that id is yours to answer")
    ResponseEntity<Void> decline(@PathVariable UUID id);

    @DeleteMapping("/{userId}")
    @Operation(
            summary = "End a friendship, or withdraw a request you sent",
            description = "Idempotent: removing a friendship that is not there succeeds.")
    @ApiResponse(responseCode = "204", description = "Gone")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<Void> remove(@PathVariable UUID userId);
}
