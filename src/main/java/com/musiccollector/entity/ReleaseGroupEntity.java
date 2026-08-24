package com.musiccollector.entity;

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

@Entity
@Table(name = "release_groups")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ReleaseGroupEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID mbid;

    @Column(nullable = false)
    private String title;

    @Column(name = "artist_name", nullable = false)
    private String artistName;

    @Column(name = "artist_mbid")
    private UUID artistMbid;

    @Column(name = "first_release_year")
    private Integer firstReleaseYear;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
