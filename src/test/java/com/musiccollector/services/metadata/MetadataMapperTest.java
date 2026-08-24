package com.musiccollector.services.metadata;

import com.musiccollector.client.MusicBrainzResponses;
import com.musiccollector.entity.ReleaseEntity;
import com.musiccollector.model.core.Format;
import com.musiccollector.model.core.Format;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataMapperTest {

    private static MusicBrainzResponses.Release release(
            List<MusicBrainzResponses.ArtistCredit> credits,
            List<MusicBrainzResponses.LabelInfo> labels,
            List<MusicBrainzResponses.Medium> media,
            String date) {
        return new MusicBrainzResponses.Release(
                "b0a6f7a4-0000-4000-8000-000000000001",
                "Remain in Light",
                date,
                "US",
                "075992609524",
                credits,
                new MusicBrainzResponses.ReleaseGroup("b0a6f7a4-0000-4000-8000-000000000002", "Remain in Light", "1980"),
                labels,
                media,
                null);
    }

    @Test
    void joinsMultipleArtistCredits() {
        var credits = List.of(
                new MusicBrainzResponses.ArtistCredit("Brian Eno", null),
                new MusicBrainzResponses.ArtistCredit("David Byrne", null));

        assertThat(MetadataMapper.artistName(release(credits, null, null, "1981")))
                .isEqualTo("Brian Eno, David Byrne");
    }

    @Test
    void survivesAReleaseWithNoArtistCredit() {
        assertThat(MetadataMapper.artistName(release(null, null, null, "1980"))).isEqualTo("Unknown artist");
    }

    @Test
    void readsTheYearOutOfPartialDates() {
        // MusicBrainz dates are partial far more often than they are complete.
        assertThat(MetadataMapper.year("1980-10-08")).isEqualTo(1980);
        assertThat(MetadataMapper.year("1980-10")).isEqualTo(1980);
        assertThat(MetadataMapper.year("1980")).isEqualTo(1980);
    }

    @Test
    void treatsUnusableDatesAsUnknown() {
        assertThat(MetadataMapper.year(null)).isNull();
        assertThat(MetadataMapper.year("")).isNull();
        assertThat(MetadataMapper.year("19")).isNull();
        assertThat(MetadataMapper.year("no-date")).isNull();
    }

    @Test
    void takesTheFormatFromTheFirstMedium() {
        var media = List.of(new MusicBrainzResponses.Medium("12\" Vinyl"), new MusicBrainzResponses.Medium("CD"));

        assertThat(MetadataMapper.format(release(null, null, media, "1980"))).isEqualTo(Format.VINYL);
    }

    @Test
    void readsLabelAndCatalogNumberTogether() {
        var labels = List.of(new MusicBrainzResponses.LabelInfo(
                "SRK 6095", new MusicBrainzResponses.Label("Sire")));
        var r = release(null, labels, null, "1980");

        assertThat(MetadataMapper.label(r)).isEqualTo("Sire");
        assertThat(MetadataMapper.catalogNumber(r)).isEqualTo("SRK 6095");
    }

    @Test
    void skipsEmptyLabelEntries() {
        // MusicBrainz emits placeholder label-info objects with both fields null.
        var labels = List.of(
                new MusicBrainzResponses.LabelInfo(null, null),
                new MusicBrainzResponses.LabelInfo("SRK 6095", new MusicBrainzResponses.Label("Sire")));

        assertThat(MetadataMapper.catalogNumber(release(null, labels, null, "1980"))).isEqualTo("SRK 6095");
    }

    @Test
    void reportsNoLabelRatherThanFailing() {
        assertThat(MetadataMapper.label(release(null, null, null, "1980"))).isNull();
        assertThat(MetadataMapper.catalogNumber(release(null, List.of(), null, "1980"))).isNull();
    }

    @Test
    void withholdsTheCoverUrlOnlyWhenItIsKnownToBeEmpty() {
        // The URL is built from the mbid and exists whether or not the archive holds any
        // bytes, so a definite "no cover" has to be the only thing that nulls it out.
        assertThat(MetadataMapper.toDto(releaseEntity(Boolean.FALSE), GROUP).coverArtUrl()).isNull();
        assertThat(MetadataMapper.toDto(releaseEntity(Boolean.TRUE), GROUP).coverArtUrl()).isEqualTo(COVER_URL);
        // Unknown is not a no: a release persisted from a search has never been probed, and
        // hiding a cover that does exist is the worse of the two mistakes.
        assertThat(MetadataMapper.toDto(releaseEntity(null), GROUP).coverArtUrl()).isEqualTo(COVER_URL);
    }

    private static final UUID GROUP = UUID.fromString("b0a6f7a4-0000-4000-8000-000000000002");
    private static final String COVER_URL =
            "https://coverartarchive.org/release/b0a6f7a4-0000-4000-8000-000000000001/front-500";

    private static ReleaseEntity releaseEntity(Boolean hasCoverArt) {
        ReleaseEntity entity = new ReleaseEntity();
        entity.setId(UUID.randomUUID());
        entity.setMbid(UUID.fromString("b0a6f7a4-0000-4000-8000-000000000001"));
        entity.setTitle("Remain in Light");
        entity.setArtistName("Talking Heads");
        entity.setFormat(Format.VINYL);
        entity.setCoverArtUrl(COVER_URL);
        entity.setHasCoverArt(hasCoverArt);
        return entity;
    }
}
