package com.musiccollector.entity;

import com.musiccollector.model.core.FriendshipStatus;
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
 * One mutual friendship, or a request to become one.
 *
 * Mutual means one row: a row per direction would let "are these two friends" have two
 * answers. Which side asked is still recorded, because only the addressee may accept.
 */
@Entity
@Table(name = "friendships")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class FriendshipEntity {

    @Id
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "addressee_id", nullable = false)
    private UUID addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendshipStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    /** The other party, seen from one side of the pair. */
    public UUID otherThan(UUID userId) {
        return requesterId.equals(userId) ? addresseeId : requesterId;
    }

    public boolean involves(UUID userId) {
        return requesterId.equals(userId) || addresseeId.equals(userId);
    }
}
