package com.rekordo.repository;

import com.rekordo.entity.FriendshipEntity;
import com.rekordo.model.core.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<FriendshipEntity, UUID> {

    /**
     * The row for a pair, whichever way round it was created. Every read has to ask it this
     * way: looking a friendship up by direction is how "we are friends" ends up true on one
     * screen and false on another.
     */
    @Query("""
            SELECT f FROM FriendshipEntity f
            WHERE (f.requesterId = :a AND f.addresseeId = :b)
               OR (f.requesterId = :b AND f.addresseeId = :a)
            """)
    Optional<FriendshipEntity> findBetween(@Param("a") UUID a, @Param("b") UUID b);

    @Query("""
            SELECT f FROM FriendshipEntity f
            WHERE (f.requesterId = :userId OR f.addresseeId = :userId) AND f.status = :status
            ORDER BY f.createdAt DESC
            """)
    List<FriendshipEntity> findAllByUserAndStatus(
            @Param("userId") UUID userId, @Param("status") FriendshipStatus status);

    /** Requests waiting for this user to answer — the pinned cards at the top of Friends. */
    List<FriendshipEntity> findAllByAddresseeIdAndStatusOrderByCreatedAtDesc(
            UUID addresseeId, FriendshipStatus status);

    /** Requests this user sent and nobody has answered yet, so the button can say "Requested". */
    List<FriendshipEntity> findAllByRequesterIdAndStatusOrderByCreatedAtDesc(
            UUID requesterId, FriendshipStatus status);

    @Query("""
            SELECT CASE WHEN f.requesterId = :userId THEN f.addresseeId ELSE f.requesterId END
            FROM FriendshipEntity f
            WHERE (f.requesterId = :userId OR f.addresseeId = :userId)
              AND f.status = com.rekordo.model.core.FriendshipStatus.ACCEPTED
            """)
    List<UUID> findFriendIds(@Param("userId") UUID userId);
}
