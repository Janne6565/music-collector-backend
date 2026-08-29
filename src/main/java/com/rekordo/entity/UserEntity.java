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

    /** What the app calls you. Null for accounts made before there was a field for it. */
    @Column(name = "display_name")
    private String displayName;

    /**
     * The public identifier other collectors search for. Null until Friends is opened for
     * the first time -- the rest of the app never needs one, so claiming it is not part of
     * signing up.
     */
    @Column
    private String handle;

    /**
     * The object key of the profile picture, or null while there is none -- which is the
     * normal case. Turn 27 offers the picture in one row on Account and asks for it
     * nowhere, so an account without one is not an account that skipped a step.
     */
    @Column(name = "avatar_key")
    private String avatarKey;

    /**
     * When the current picture landed. The key does not change when a picture is replaced,
     * so this is what makes the public URL a different URL and gets past the caches.
     */
    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    /**
     * What the rendered picture weighs in object storage, or null for one written before
     * the column existed. It is the only stored object an account owns that has no row of
     * its own to be measured from, and the storage allowance has to add it up with the
     * rest.
     */
    @Column(name = "avatar_bytes")
    private Long avatarBytes;

    /**
     * When the address was proved reachable, or null while it has not been. Nothing is gated
     * on it -- see EmailVerificationService for why -- so it is a fact about the mailbox
     * rather than a permission.
     */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

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
