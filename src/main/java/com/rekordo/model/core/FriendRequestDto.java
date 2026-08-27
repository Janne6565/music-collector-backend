package com.rekordo.model.core;

import java.time.Instant;
import java.util.UUID;

/**
 * A request waiting to be answered — the card pinned above the feed in 15a and 15g.
 *
 * @param mutualFriends the "4 friends in common" line. The strongest signal that a request
 *                      is from someone real, and the only reason to compute it.
 */
public record FriendRequestDto(
        UUID id, ProfileSummaryDto from, Instant createdAt, long mutualFriends) {}
