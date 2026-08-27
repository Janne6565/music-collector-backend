package com.rekordo.model.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalRefTest {

    @Test
    void survivesARoundTripThroughItsStoredForm() {
        // This is the whole contract: what goes into the database comes back meaning the
        // same thing, for both catalogues.
        for (String stored : new String[] {
            "musicbrainz:a9e30282-5b37-3f92-b897-b9659a1a312b", "discogs:31679120"
        }) {
            assertThat(ExternalRef.parse(stored)).hasToString(stored);
        }
    }

    @Test
    void keepsTheSourceAndTheIdTogether() {
        ExternalRef ref = ExternalRef.parse("discogs:31679120");

        assertThat(ref.source()).isEqualTo(ReleaseSource.DISCOGS);
        assertThat(ref.id()).isEqualTo("31679120");
    }

    @Test
    void readsAnUnprefixedIdAsMusicBrainz() {
        // Every id written before two sources existed came from MusicBrainz. The migration
        // prefixes what is in the database, but a client that has not synced since can
        // still push an old-style id, and stranding that collection would be the worse
        // failure.
        ExternalRef ref = ExternalRef.parse("a9e30282-5b37-3f92-b897-b9659a1a312b");

        assertThat(ref.source()).isEqualTo(ReleaseSource.MUSICBRAINZ);
        assertThat(ref.id()).isEqualTo("a9e30282-5b37-3f92-b897-b9659a1a312b");
    }

    @Test
    void keepsEverythingAfterTheFirstSeparator() {
        // A Discogs id is numeric today, but nothing guarantees a future id has no colon
        // in it, and splitting on the last one would silently corrupt those.
        assertThat(ExternalRef.parse("discogs:a:b:c").id()).isEqualTo("a:b:c");
    }

    @Test
    void treatsAnUnknownSourceAsMusicBrainzRatherThanFailing() {
        // A client one version ahead could name a catalogue this build has never heard of.
        // Refusing the whole sync batch over one such copy would be a poor trade.
        assertThat(ExternalRef.parse("bandcamp:12345").source()).isEqualTo(ReleaseSource.MUSICBRAINZ);
    }

    @Test
    void refusesAReferenceThatIdentifiesNothing() {
        assertThatThrownBy(() -> ExternalRef.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExternalRef.parse("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExternalRef.parse("discogs:")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExternalRef(null, "1")).isInstanceOf(IllegalArgumentException.class);
    }
}
