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

/** A completed external sign-in waiting to be collected by the app that started it. */
@Entity
@Table(name = "oauth_handoffs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class OAuthHandoffEntity {

    /** SHA-256 of the code that went into the deep link. The raw value is never stored. */
    @Id
    @Column(name = "code_hash")
    private String codeHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
