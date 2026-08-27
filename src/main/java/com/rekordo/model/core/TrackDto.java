package com.rekordo.model.core;

/**
 * One row of a tracklist, ready to draw (design 26a).
 *
 * @param number     the catalogue's own label — "1", "A1", "C1". Never derived from the
 *                   position: showing it verbatim is what keeps a double LP reading
 *                   A1 → D6 instead of 1 → 26.
 * @param lengthMs   milliseconds, or null. Null is common and is not an error; the sheet
 *                   leaves the duration cell empty rather than drawing a dash.
 * @param artistName the track's own credit, or null when it is the release's. Sent only
 *                   when it differs, so a Various Artists record carries a credit on every
 *                   row and a normal album carries none.
 */
public record TrackDto(String number, String title, Integer lengthMs, String artistName) {}
