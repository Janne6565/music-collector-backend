package com.musiccollector.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** The slice of the MusicBrainz web-service JSON this app actually reads. */
public final class MusicBrainzResponses {

    private MusicBrainzResponses() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResponse(int count, List<Release> releases) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Release(
            String id,
            String title,
            String date,
            String country,
            String barcode,
            @JsonProperty("artist-credit") List<ArtistCredit> artistCredit,
            @JsonProperty("release-group") ReleaseGroup releaseGroup,
            @JsonProperty("label-info") List<LabelInfo> labelInfo,
            List<Medium> media,
            /** Present on a lookup, absent from search results — so null means "unknown". */
            @JsonProperty("cover-art-archive") CoverArtArchive coverArtArchive) {}

    /** What the Cover Art Archive holds for a release, as MusicBrainz reports it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CoverArtArchive(Boolean front, Boolean artwork, Integer count) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArtistCredit(String name, Artist artist) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artist(String id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReleaseGroup(
            String id, String title, @JsonProperty("first-release-date") String firstReleaseDate) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LabelInfo(@JsonProperty("catalog-number") String catalogNumber, Label label) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Label(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Medium(String format) {}
}
