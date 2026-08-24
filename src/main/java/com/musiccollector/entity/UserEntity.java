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
 * An account. Accounts are optional: the app is fully usable with no row in this table,
 * and signing in only adds cross-device sync.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Bumped to revoke every outstanding refresh token for this user at once. A refresh
     * token carrying an older version is rejected, so "sign out everywhere" is one write.
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
