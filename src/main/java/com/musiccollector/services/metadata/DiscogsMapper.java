package com.musiccollector.services.metadata;

import com.musiccollector.client.DiscogsResponses;
import com.musiccollector.model.core.ExternalRef;
import com.musiccollector.model.core.Format;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Turning a Discogs row into the shape the rest of the app already speaks.
 *
 * <p>Discogs is community-entered and it shows. Nearly every field is optional, several are
 * lists where one value is expected, and at least one is routinely used for something other
 * than what it is named. Everything here is defensive on purpose.
 */
public final class DiscogsMapper {

    /** A barcode is 8 to 14 digits once the spaces and dashes people type are removed. */
    private static final Pattern BARCODE = Pattern.compile("\\d{8,14}");

    private static final String ARTIST_TITLE_SEPARATOR = " - ";

    private DiscogsMapper() {}

    /**
     * Discogs sends "Artist - Title" as one string and never the two apart.
     *
     * <p>Split on the first separator, not the last: "Miles Davis - Bitches Brew - Live"
     * is an album with a dash in its name, not an artist called "Miles Davis - Bitches
     * Brew".
     */
    public static String artistOf(String combined) {
        if (combined == null) {
            return "Unknown artist";
        }
        int split = combined.indexOf(ARTIST_TITLE_SEPARATOR);
        return split < 0 ? "Unknown artist" : combined.substring(0, split).trim();
    }

    public static String titleOf(String combined) {
        if (combined == null) {
            return "Untitled";
        }
        int split = combined.indexOf(ARTIST_TITLE_SEPARATOR);
        return split < 0
                ? combined.trim()
                : combined.substring(split + ARTIST_TITLE_SEPARATOR.length()).trim();
    }

    public static Format formatOf(DiscogsResponses.SearchResult result) {
        List<DiscogsResponses.ReleaseFormat> formats = result.formats();
        if (formats == null || formats.isEmpty()) {
            return Format.OTHER;
        }
        // A release can span media (an LP with a bonus CD); the first names the edition,
        // matching how the MusicBrainz side already decides.
        return Format.fromMediumName(formats.getFirst().name());
    }

    /**
     * The first entry that is actually a barcode.
     *
     * <p>Discogs' barcode field is free text and holds whatever the contributor typed: one
     * pressing of <em>ten days</em> lists {@code "VF421 / TEN012 A ten days that meant
     * something Stu"} there. Storing that would poison barcode lookup, which is the one
     * identifier a scan can rely on.
     */
    public static String barcodeOf(DiscogsResponses.SearchResult result) {
        if (result.barcode() == null) {
            return null;
        }
        for (String candidate : result.barcode()) {
            if (candidate == null) {
                continue;
            }
            String digits = candidate.replaceAll("[\\s-]", "");
            if (BARCODE.matcher(digits).matches()) {
                return digits;
            }
        }
        return null;
    }

    public static String labelOf(DiscogsResponses.SearchResult result) {
        List<String> labels = result.label();
        if (labels == null || labels.isEmpty()) {
            return null;
        }
        return labels.getFirst();
    }

    /**
     * The album this pressing belongs to.
     *
     * <p>A Discogs master groups pressings the way a release group does. A release with no
     * master is its own album — a one-off pressing nobody has grouped yet — and keying it
     * on the release keeps it addressable rather than lumping every such record together.
     */
    public static String albumRefOf(DiscogsResponses.SearchResult result) {
        return result.masterId() != null && result.masterId() > 0
                ? ExternalRef.discogs(String.valueOf(result.masterId())).toString()
                : ExternalRef.discogs("release-" + result.id()).toString();
    }

    public static String releaseRefOf(DiscogsResponses.SearchResult result) {
        return ExternalRef.discogs(String.valueOf(result.id())).toString();
    }

    /** Blank rather than absent is how Discogs says "no image for an anonymous caller". */
    public static String coverUrlOf(DiscogsResponses.SearchResult result) {
        String cover = result.coverImage();
        return cover == null || cover.isBlank() ? null : cover;
    }
}
