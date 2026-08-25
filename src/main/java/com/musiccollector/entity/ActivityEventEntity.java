package com.musiccollector.entity;

import com.musiccollector.model.core.ActivityType;
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

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
