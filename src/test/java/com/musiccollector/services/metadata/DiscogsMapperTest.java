package com.musiccollector.services.metadata;

import com.musiccollector.client.DiscogsResponses;
import com.musiccollector.model.core.Format;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiscogsMapperTest {

    private static DiscogsResponses.SearchResult result(
            String title, List<String> barcode, List<DiscogsResponses.ReleaseFormat> formats, Long masterId) {
        return new DiscogsResponses.SearchResult(
                31679120L, masterId, title, 2024, "UK & Ireland", "TEN012",
                List.of("Atlantic Records UK", "The Vinyl Factory"), barcode, formats, "", "");
    }

    @Test
    void splitsArtistFromTitleOnTheFirstSeparator() {
        // Discogs sends "Artist - Title" as one string and never the two apart.
        assertThat(DiscogsMapper.artistOf("Fred again.. - Ten Days")).isEqualTo("Fred again..");
        assertThat(DiscogsMapper.titleOf("Fred again.. - Ten Days")).isEqualTo("Ten Days");

        // Splitting on the last separator would read this as an artist called
        // "Miles Davis - Bitches Brew".
        assertThat(DiscogsMapper.artistOf("Miles Davis - Bitches Brew - Live")).isEqualTo("Miles Davis");
        assertThat(DiscogsMapper.titleOf("Miles Davis - Bitches Brew - Live"))
                .isEqualTo("Bitches Brew - Live");
    }

    @Test
    void copesWithATitleThatHasNoArtistInIt() {
        assertThat(DiscogsMapper.artistOf("Untitled")).isEqualTo("Unknown artist");
        assertThat(DiscogsMapper.titleOf("Untitled")).isEqualTo("Untitled");
        assertThat(DiscogsMapper.artistOf(null)).isEqualTo("Unknown artist");
        assertThat(DiscogsMapper.titleOf(null)).isEqualTo("Untitled");
    }

    @Test
    void ignoresABarcodeFieldThatHoldsSomethingElse() {
        // Real data. One pressing of "ten days" lists its runout-groove text in the barcode
        // field; storing that would poison barcode lookup, which is the one identifier a
        // scan can rely on.
        var runout = result(
                "Fred again.. - Ten Days",
                List.of("VF421 / TEN012 A   ten days that meant something Stu", "VF421 / TEN012 B"),
                null,
                null);

        assertThat(DiscogsMapper.barcodeOf(runout)).isNull();
    }

    @Test
    void findsTheBarcodeAmongWhateverElseIsInThere() {
        var mixed = result(
                "Fred again.. - Ten Days", List.of("VF421 / TEN012 A", "5 054197 696855"), null, null);

        // Spaces and dashes are how people type a barcode off a sleeve.
        assertThat(DiscogsMapper.barcodeOf(mixed)).isEqualTo("5054197696855");
    }

    @Test
    void readsTheFormatFromTheFirstMedium() {
        var vinyl = result(
                "Fred again.. - Ten Days",
                null,
                List.of(new DiscogsResponses.ReleaseFormat("Vinyl", "1", List.of("LP", "Album"))),
                null);
        assertThat(DiscogsMapper.formatOf(vinyl)).isEqualTo(Format.VINYL);

        // Discogs calls a download "File"; the shared matcher already folds that to DIGITAL.
        var file = result(
                "Fred again.. - Ten Days",
                null,
                List.of(new DiscogsResponses.ReleaseFormat("File", "1", List.of("FLAC"))),
                null);
        assertThat(DiscogsMapper.formatOf(file)).isEqualTo(Format.DIGITAL);

        assertThat(DiscogsMapper.formatOf(result("x - y", null, null, null))).isEqualTo(Format.OTHER);
    }

    @Test
    void givesAnUngroupedPressingAnAlbumOfItsOwn() {
        // A release with no master is a one-off nobody has grouped yet. Keying it on the
        // release keeps it addressable; keying every such record on "no master" would lump
        // them into one nonsense album.
        assertThat(DiscogsMapper.albumRefOf(result("x - y", null, null, 3721005L)))
                .isEqualTo("discogs:3721005");
        assertThat(DiscogsMapper.albumRefOf(result("x - y", null, null, null)))
                .isEqualTo("discogs:release-31679120");
        assertThat(DiscogsMapper.albumRefOf(result("x - y", null, null, 0L)))
                .isEqualTo("discogs:release-31679120");
    }

    @Test
    void treatsAnEmptyCoverAsNoCover() {
        // Discogs returns "" rather than omitting the field when the caller has no token.
        assertThat(DiscogsMapper.coverUrlOf(result("x - y", null, null, null))).isNull();
    }
}
