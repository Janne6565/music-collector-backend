package com.rekordo.model.core;

import java.util.Locale;

/**
 * The four formats the app tracks. Both catalogues report a much longer, messier list —
 * MusicBrainz says {@code 12" Vinyl}, {@code Enhanced CD}, {@code Digital Media}; Discogs
 * says {@code Vinyl}, {@code CD}, {@code Cassette}, {@code File} — so everything folds into
 * these four plus OTHER. The two vocabularies overlap enough that one matcher serves both.
 */
public enum Format {
    VINYL,
    CD,
    CASSETTE,
    DIGITAL,
    OTHER;

    public static Format fromMediumName(String mediaFormat) {
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
