package com.rekordo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One row of a release's tracklist, as the catalogue words it.
 *
 * <p>Nothing here is recomputed. {@link #number} is the catalogue's own label — "1" on a CD,
 * "A1" on vinyl, and "C1" on the second LP of a double — so a reader that renumbered from
 * {@link #position} would silently destroy the side breaks a vinyl owner is looking for.
 */
@Entity
@Table(name = "release_tracks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ReleaseTrackEntity {

    @Id
    private UUID id;

    @Column(name = "release_id", nullable = false)
    private UUID releaseId;

    @Column(name = "medium_position", nullable = false)
    private int mediumPosition;

    @Column(name = "medium_format")
    private String mediumFormat;

    /** MusicBrainz sends "" for an unnamed disc far more often than null. Both mean unnamed. */
    @Column(name = "medium_title")
    private String mediumTitle;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private String title;

    /** Milliseconds, or null — routinely absent for individual tracks of a complete disc. */
    @Column(name = "length_ms")
    private Integer lengthMs;

    /** The track's own credit, kept even when it matches the release's. */
    @Column(name = "artist_name")
    private String artistName;
}
