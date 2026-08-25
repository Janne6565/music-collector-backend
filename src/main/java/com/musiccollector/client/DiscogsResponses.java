package com.musiccollector.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** The slice of the Discogs API this app reads. */
public final class DiscogsResponses {

    private DiscogsResponses() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResponse(Pagination pagination, List<SearchResult> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagination(int items, int pages, int page) {}

    /**
     * One row of a database search.
     *
     * <p>Nearly every field is a maybe. Discogs is community-entered, and it shows: the
     * barcode of one <em>ten days</em> pressing is the runout-groove text rather than a
     * barcode. Nothing here may be trusted to be well-formed.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResult(
            Long id,
            /** The master groups a record's pressings, the way a release group does. */
            @JsonProperty("master_id") Long masterId,
            /** "Artist - Title", undivided. Discogs does not send the two apart. */
            String title,
            Integer year,
            String country,
            String catno,
            List<String> label,
            /** Free text, and frequently not a barcode at all. */
            List<String> barcode,
            List<ReleaseFormat> formats,
            /** Empty unless the request carried a token. */
            String thumb,
            @JsonProperty("cover_image") String coverImage) {}

    /**
     * A format and what kind of one it is.
     *
     * <p>{@code name} is the medium ("Vinyl", "CD", "Cassette", "File"); {@code
     * descriptions} carries what collectors actually care about — "LP", "12\"", "45 RPM",
     * "Limited Edition", "White Label", "Reissue".
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReleaseFormat(String name, String qty, List<String> descriptions) {}
}
