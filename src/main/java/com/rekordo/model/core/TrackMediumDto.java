package com.rekordo.model.core;

import java.util.List;

/**
 * One disc, LP or tape of a release.
 *
 * @param position 1-based, in catalogue order. The client heads a medium "CD · 2 of 8" and
 *                 draws no heading at all for a single-medium release, where the format is
 *                 already stated in the facts above the tracklist.
 * @param title    a named disc, which happens on box sets. Empty titles are normalised to
 *                 null here — MusicBrainz sends "" for "unnamed" far more often than null,
 *                 and a client should not have to know that.
 */
public record TrackMediumDto(int position, String format, String title, List<TrackDto> tracks) {}
