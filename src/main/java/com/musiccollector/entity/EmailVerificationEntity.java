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
@Table(name = "email_verifications")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class EmailVerificationEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SHA-256 of the token that was e-mailed. The raw value is never stored. */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    /**
     * The address this row moves the account to, or null when it confirms the address the
     * account already carries. A change is a confirmation with somewhere else to put the
     * answer, which is why one table covers both.
     */
    @Column(name = "new_email")
    private String newEmail;

    /** What to put back if the change is cancelled, including its original case. */
    @Column(name = "previous_email")
    private String previousEmail;

    /**
     * The old mailbox's undo. Outlives the change by a day rather than expiring with the
     * link: it is the only defence if somebody else is at the keyboard, and they would
     * otherwise only have to wait.
     */
    @Column(name = "cancel_token_hash")
    private String cancelTokenHash;

    @Column(name = "cancel_expires_at")
    private Instant cancelExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
