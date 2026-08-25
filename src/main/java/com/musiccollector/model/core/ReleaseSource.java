package com.musiccollector.model.core;

/** The catalogues this app reads. */
public enum ReleaseSource {
    MUSICBRAINZ("musicbrainz"),
    DISCOGS("discogs");

    private final String prefix;

    ReleaseSource(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    /**
     * Unknown prefixes fall back to MusicBrainz rather than throwing.
     *
     * <p>A client one version ahead could push a source this build has never heard of, and
     * refusing the whole sync batch over one unrecognised copy would be a poor trade — the
     * record still round-trips, it just resolves against the wrong catalogue until the
     * server catches up.
     */
    public static ReleaseSource fromPrefix(String prefix) {
        for (ReleaseSource source : values()) {
            if (source.prefix.equalsIgnoreCase(prefix)) {
                return source;
            }
        }
        return MUSICBRAINZ;
    }
}
