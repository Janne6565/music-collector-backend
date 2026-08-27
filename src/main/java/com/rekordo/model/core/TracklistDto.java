package com.rekordo.model.core;

import java.util.List;

/**
 * A release's tracklist, or the reason it will never have one.
 *
 * <p>One shape for both, because the section on the sheet is the same section either way —
 * it states the count it already knows and then either lists the titles or says, in the
 * dashed box the deck uses for an absent fact, why it cannot. An absent tracklist is not an
 * error response: 26e is explicit that it stays visible and labelled rather than vanishing.
 *
 * @param trackCount        what the release row already knew, so the header is true before
 *                          the titles arrive and stays true when they never do.
 * @param unavailableReason null when {@code media} is the answer.
 */
public record TracklistDto(
        String releaseId,
        Integer trackCount,
        Integer discCount,
        List<TrackMediumDto> media,
        TracklistUnavailableReason unavailableReason) {}
