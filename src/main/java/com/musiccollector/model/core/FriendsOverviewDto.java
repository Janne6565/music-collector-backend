package com.musiccollector.model.core;

import java.util.List;

/**
 * Everything the Friends screen needs about people, in one request.
 *
 * <p>One call rather than three, because the mobile tab draws requests, the People count
 * and the list together and three separate loading states for one screen is three chances
 * to render a half-finished panel.
 */
public record FriendsOverviewDto(
        List<ProfileSummaryDto> friends,
        List<FriendRequestDto> incoming,
        /** Requests this account has sent and nobody has answered. */
        List<ProfileSummaryDto> outgoing) {}
