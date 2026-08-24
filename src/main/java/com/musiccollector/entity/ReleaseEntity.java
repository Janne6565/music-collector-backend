package com.musiccollector.entity;

import com.musiccollector.model.core.Format;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "releases")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ReleaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID mbid;

    @Column(name = "release_group_id", nullable = false)
    private UUID releaseGroupId;

    @Column(nullable = false)
    private String title;

    @Column(name = "artist_name", nullable = false)
    private String artistName;

    // Stored as its name in a plain TEXT column — the migration deliberately adds no CHECK
    // constraint, so a new format is a code change rather than a schema migration.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Format format;

    private Integer year;

    private String label;

    @Column(name = "catalog_number")
    private String catalogNumber;

    private String country;

    private String barcode;

    @Column(name = "cover_art_url")
    private String coverArtUrl;

    /**
     * Whether the Cover Art Archive actually has a front cover. Null until something has
     * asked — a release persisted from a search has not been probed yet.
     */
    @Column(name = "has_cover_art")
    private Boolean hasCoverArt;

    /** The full date as MusicBrainz holds it, which may be partial: "1970" or "1970-03-30". */
    @Column(name = "release_date")
    private String releaseDate;

    @Column(name = "track_count")
    private Integer trackCount;

    @Column(name = "disc_count")
    private Integer discCount;

    @Column(name = "dominant_color")
    private String dominantColor;

    @Column(name = "accent_color")
    private String accentColor;

    @Column(name = "lightness")
    private Double lightness;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
