package com.rekordo.entity;

import com.rekordo.model.core.ActivityType;
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

/**
 * One line of somebody's activity.
 *
 * <p>The title and artist are copied in rather than looked up later. The release mirror is
 * a cache any client may drop, and a feed line that loses its title months afterwards is
 * worse than one that cannot be re-resolved.
 */
@Entity
@Table(name = "activity_events")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ActivityEventEntity {

    @Id
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "release_id")
    private String releaseId;

    @Column
    private String title;

    @Column(name = "artist_name")
    private String artistName;

    /**
     * The format a WISH_ADDED line was hunting for, or null on every other type and on a
     * wish that wants any.
     *
     * <p>A string rather than an enum, as {@code wishlist_items} stores it: what a wish
     * wants is a subset of the copy formats, and mapping it through {@link
     * com.rekordo.model.core.Format} here would put a check constraint on the column that
     * a later format would have to migrate around.
     */
    @Column(name = "wanted_format")
    private String wantedFormat;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
