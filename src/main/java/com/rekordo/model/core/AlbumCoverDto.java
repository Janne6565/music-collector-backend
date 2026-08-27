package com.rekordo.model.core;

/**
 * The artwork that stands for an album, for a screen that holds albums rather than
 * pressings.
 *
 * <p>A wishlist entry is a want for an <em>album</em> in a format — it names no pressing,
 * so it carries no cover of its own. The album's picture is therefore resolved rather
 * than stored: an album is only ever an id plus a title on the device that wants it.
 */
public record AlbumCoverDto(
        /** Source-qualified, exactly as it was asked for: "musicbrainz:<uuid>" or "discogs:<int>". */
        String albumId,
        /** The cover to render, or null when nothing known has one. */
        String coverArtUrl) {}
