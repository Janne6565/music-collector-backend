package com.rekordo.repository;

import com.rekordo.entity.ActivityEventEntity;
import com.rekordo.model.core.ActivityType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<ActivityEventEntity, UUID> {

    /**
     * Everything the people in {@code actorIds} have done, newest first.
     *
     * <p>Read from the actors' rows at request time rather than fanned out into a per-viewer
     * inbox. A shelf that closes has to take its history with it, and rewriting everybody's
     * inbox on a settings change is a job that can fail halfway.
     */
    @Query("""
            SELECT e FROM ActivityEventEntity e
            WHERE e.actorId IN :actorIds
            ORDER BY e.occurredAt DESC, e.recordedAt DESC
            """)
    List<ActivityEventEntity> feedFor(@Param("actorIds") Collection<UUID> actorIds, Pageable pageable);

    /**
     * The same read, bounded to a window — what the Sunday digest covers.
     *
     * <p>Bounded in the query rather than filtered afterwards: a week is a handful of rows
     * for most people and the whole history for nobody, and reading everything to throw
     * most of it away gets worse every year the app exists.
     */
    @Query("""
            SELECT e FROM ActivityEventEntity e
            WHERE e.actorId IN :actorIds AND e.occurredAt >= :since
            ORDER BY e.occurredAt DESC, e.recordedAt DESC
            """)
    List<ActivityEventEntity> feedSince(
            @Param("actorIds") Collection<UUID> actorIds,
            @Param("since") java.time.Instant since,
            Pageable pageable);

    boolean existsByActorIdAndTypeAndSubjectId(UUID actorId, ActivityType type, UUID subjectId);

    void deleteAllByActorIdAndSubjectId(UUID actorId, UUID subjectId);
}
