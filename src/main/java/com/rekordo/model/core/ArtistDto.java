package com.rekordo.model.core;

import java.util.UUID;

/**
 * An artist as a search result and as the header of screen 10c/10d.
 *
 * <p>{@code disambiguation} is not decoration: MusicBrainz holds several distinct artists
 * called "Daughter", and that one line ("UK indie folk band fronted by Elena Tonra") is
 * frequently the only thing that tells them apart. It is carried on the row, not just on
 * the artist screen.
 *
 * <p>{@code score} is MusicBrainz's own match confidence, 0-100. An exact name scores 100
 * and substring matches trail below, which is what lets a client rank "Daughter" above
 * "Anyone's Daughter" when both were returned.
 */
public record ArtistDto(
        UUID mbid,
        String name,
        String disambiguation,
        /** "Group" or "Person", as MusicBrainz classifies it. Null when unknown. */
        String type,
        String country,
        String beganIn,
        String endedIn,
        Integer score) {}
