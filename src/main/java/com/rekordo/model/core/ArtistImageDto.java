package com.rekordo.model.core;

/**
 * An artist's portrait, for the rows of screen 10b and the header of 10c/10d.
 *
 * <p>A one-field object rather than a bare string because "there is no picture" is a real
 * and common answer that a client has to render — a striped initial, not a broken image —
 * and a 200 carrying {@code null} says that far more plainly than a 404 does. A 404 here
 * would mean "no such artist", which is a different thing entirely.
 */
public record ArtistImageDto(
        /** Discogs' 150px thumbnail, or null when this artist has no picture. */
        String imageUrl) {}
