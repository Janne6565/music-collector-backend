package com.rekordo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One artist's portrait, resolved once and kept.
 *
 * <p>The row existing is itself the answer to "has anybody looked?", so a null
 * {@code imageUrl} means "looked, and there is none" rather than "not asked yet" — see
 * V14 for why that distinction earns a table of its own.
 */
@Entity
@Table(name = "artist_images")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ArtistImageEntity {

    /** The MusicBrainz artist id — the only id the rest of the app knows an artist by. */
    @Id
    private UUID mbid;

    /** Discogs' 150px thumbnail, or null when the artist has no picture to show. */
    @Column(name = "image_url")
    private String imageUrl;

    /** Which Discogs artist this resolved to, so a wrong portrait can be traced. */
    @Column(name = "discogs_artist_id")
    private Long discogsArtistId;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
