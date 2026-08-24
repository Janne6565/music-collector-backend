package com.musiccollector.services.metadata;

import com.musiccollector.client.MusicBrainzResponses;
import com.musiccollector.entity.ReleaseEntity;
import com.musiccollector.model.core.CoverThemeDto;
import com.musiccollector.model.core.Format;
import com.musiccollector.model.core.ReleaseDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Pure translation between the MusicBrainz payload, the stored row and the API DTO. */
public final class MetadataMapper {

    private MetadataMapper() {}

    public static String artistName(MusicBrainzResponses.Release release) {
        if (release.artistCredit() == null || release.artistCredit().isEmpty()) {
            return "Unknown artist";
        }
        return release.artistCredit().stream()
                .map(MusicBrainzResponses.ArtistCredit::name)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
    }

    public static UUID artistMbid(MusicBrainzResponses.Release release) {
        if (release.artistCredit() == null) {
            return null;
        }
        return release.artistCredit().stream()
                .map(MusicBrainzResponses.ArtistCredit::artist)
                .filter(artist -> artist != null && artist.id() != null)
                .findFirst()
                .map(artist -> UUID.fromString(artist.id()))
                .orElse(null);
    }

    /** MusicBrainz dates are partial: "1980", "1980-10" and "1980-10-08" all occur. */
    public static Integer year(String date) {
        if (date == null || date.length() < 4) {
            return null;
        }
        try {
            return Integer.valueOf(date.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Format format(MusicBrainzResponses.Release release) {
        if (release.media() == null || release.media().isEmpty()) {
            return Format.OTHER;
        }
        // A release can span media (a CD+DVD set); the first medium names the edition.
        return Format.fromMusicBrainz(release.media().getFirst().format());
    }

    public static String label(MusicBrainzResponses.Release release) {
        return firstLabelInfo(release)
                .map(MusicBrainzResponses.LabelInfo::label)
                .map(MusicBrainzResponses.Label::name)
                .orElse(null);
    }

    public static String catalogNumber(MusicBrainzResponses.Release release) {
        return firstLabelInfo(release)
                .map(MusicBrainzResponses.LabelInfo::catalogNumber)
                .orElse(null);
    }

    private static Optional<MusicBrainzResponses.LabelInfo> firstLabelInfo(MusicBrainzResponses.Release release) {
        List<MusicBrainzResponses.LabelInfo> infos = release.labelInfo();
        if (infos == null) {
            return Optional.empty();
        }
        return infos.stream().filter(info -> info.label() != null || info.catalogNumber() != null).findFirst();
    }

    public static ReleaseDto toDto(ReleaseEntity entity, UUID releaseGroupMbid) {
        CoverThemeDto theme = entity.getDominantColor() == null || entity.getLightness() == null
                ? null
                : new CoverThemeDto(
                        entity.getDominantColor(),
                        entity.getAccentColor(),
                        entity.getLightness(),
                        entity.getLightness() < CoverPalette.DARK_CHROME_THRESHOLD);
        return new ReleaseDto(
                entity.getId(),
                entity.getMbid(),
                releaseGroupMbid,
                entity.getTitle(),
                entity.getArtistName(),
                entity.getYear(),
                entity.getFormat(),
                entity.getLabel(),
                entity.getCatalogNumber(),
                entity.getCountry(),
                entity.getBarcode(),
                entity.getCoverArtUrl(),
                theme);
    }
}
