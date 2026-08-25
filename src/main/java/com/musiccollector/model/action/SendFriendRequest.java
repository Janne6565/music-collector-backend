package com.musiccollector.model.action;

import jakarta.validation.constraints.NotBlank;

/**
 * Asking to be friends, addressed by handle rather than by id — the handle is what the
 * person typed, and resolving it here keeps user ids off the client entirely.
 */
public record SendFriendRequest(@NotBlank String handle) {}
