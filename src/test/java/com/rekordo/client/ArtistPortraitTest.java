package com.rekordo.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two steps between "a MusicBrainz artist" and "a picture of them".
 *
 * <p>Both are places a wrong answer looks like a right one: a misparsed URL resolves to a
 * real Discogs artist who is somebody else, and the wrong image out of a set puts a record
 * sleeve where a face should be. Neither would throw.
 */
class ArtistPortraitTest {

    private static DiscogsResponses.ArtistImage image(String type, String uri, String uri150) {
        return new DiscogsResponses.ArtistImage(type, uri, uri150);
    }

    @Test
    void readsTheArtistIdOffADiscogsRelation() {
        assertThat(MusicBrainzClient.trailingId("https://www.discogs.com/artist/1055923"))
                .contains(1055923L);
    }

    @Test
    void readsTheArtistIdWhenTheUrlCarriesASlug() {
        // Both shapes are in MusicBrainz; the slug is decoration and the number is the id.
        assertThat(MusicBrainzClient.trailingId("https://www.discogs.com/artist/1055923-Daughter"))
                .contains(1055923L);
    }

    @Test
    void refusesADiscogsUrlThatIsNotAnArtist() {
        // A master or label URL would parse to a number that means something else entirely.
        assertThat(MusicBrainzClient.trailingId("https://www.discogs.com/master/12345")).isEmpty();
        assertThat(MusicBrainzClient.trailingId("https://www.discogs.com/label/678")).isEmpty();
        assertThat(MusicBrainzClient.trailingId("https://en.wikipedia.org/wiki/Daughter")).isEmpty();
    }

    @Test
    void prefersThePrimaryImageOverTheRest() {
        // Secondaries are live shots, logos and sleeve scans — wrong for a 46px circle.
        Optional<String> chosen = DiscogsClient.preferredImage(List.of(
                image("secondary", "https://i.discogs.com/live.jpg", "https://i.discogs.com/live-150.jpg"),
                image("primary", "https://i.discogs.com/band.jpg", "https://i.discogs.com/band-150.jpg")));

        assertThat(chosen).contains("https://i.discogs.com/band-150.jpg");
    }

    @Test
    void fallsBackToTheFullImageWhenThereIsNoThumbnail() {
        assertThat(DiscogsClient.preferredImage(List.of(image("primary", "https://i.discogs.com/band.jpg", ""))))
                .contains("https://i.discogs.com/band.jpg");
    }

    @Test
    void treatsDiscogsBlankStringsAsNoPicture() {
        // Blank rather than absent is how Discogs answers an unauthenticated caller. Handing
        // that to an <img> would render a broken image instead of the initial.
        assertThat(DiscogsClient.preferredImage(List.of(image("primary", "", "")))).isEmpty();
        assertThat(DiscogsClient.preferredImage(List.of())).isEmpty();
        assertThat(DiscogsClient.preferredImage(null)).isEmpty();
    }
}
