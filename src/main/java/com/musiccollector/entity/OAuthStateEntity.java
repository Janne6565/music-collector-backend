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

@Entity
@Table(name = "oauth_states")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class OAuthStateEntity {

    @Id
    private String state;

    @Column(nullable = false)
    private String provider;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set on first use, so a replayed callback cannot mint a second session. */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
