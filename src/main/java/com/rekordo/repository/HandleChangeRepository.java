package com.rekordo.repository;

import com.rekordo.entity.HandleChangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface HandleChangeRepository extends JpaRepository<HandleChangeEntity, UUID> {

    /** How many times this account has changed handle inside the window. */
    long countByUserIdAndChangedAtAfter(UUID userId, Instant after);

    /**
     * Who else has held this handle recently. A handle someone gave up stays theirs for a
     * cooling-off period, so inbound links and requests cannot be inherited by whoever
     * claims it next.
     */
    @Query("""
            SELECT h FROM HandleChangeEntity h
            WHERE LOWER(h.handle) = LOWER(:handle) AND h.userId <> :userId AND h.changedAt > :after
            """)
    List<HandleChangeEntity> findRecentClaimsByOthers(
            @Param("handle") String handle, @Param("userId") UUID userId, @Param("after") Instant after);
}
