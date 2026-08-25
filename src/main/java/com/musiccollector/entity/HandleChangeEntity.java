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

/**
 * One handle an account has held, kept after it was given up.
 *
 * Two jobs: it is the record the twice-a-year limit is counted from, and it keeps a
 * released handle reserved for a while, so the next person to claim it does not inherit
 * requests and links meant for whoever had it before.
 */
@Entity
@Table(name = "handle_changes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class HandleChangeEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String handle;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}
