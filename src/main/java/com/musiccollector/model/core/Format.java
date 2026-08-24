package com.musiccollector.model.core;

import java.util.Locale;

/**
 * The four formats the app tracks. MusicBrainz reports a much longer, messier list
 * ({@code 12" Vinyl}, {@code Enhanced CD}, {@code Digital Media}, …), so everything is
 * folded into these four plus OTHER.
 */
public enum Format {
    VINYL,
    CD,
    CASSETTE,
    DIGITAL,
    OTHER;

    public static Format fromMusicBrainz(String mediaFormat) {
        if (mediaFormat == null || mediaFormat.isBlank()) {
            return OTHER;
        }
        String normalised = mediaFormat.toLowerCase(Locale.ROOT);
        if (normalised.contains("vinyl") || normalised.contains("flexi") || normalised.contains("shellac")) {
            return VINYL;
        }
        if (normalised.contains("cassette") || normalised.contains("tape")) {
            return CASSETTE;
        }
        if (normalised.contains("digital") || normalised.contains("file") || normalised.contains("download")) {
            return DIGITAL;
        }
        // Checked after the others so "Enhanced CD" and "HDCD" land here but "CD-R Cassette"
        // style oddities are already claimed above.
        if (normalised.contains("cd") || normalised.contains("dvd") || normalised.contains("sacd")) {
            return CD;
        }
        return OTHER;
    }
}
