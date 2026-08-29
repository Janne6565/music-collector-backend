package com.rekordo.repository;

import com.rekordo.entity.PhotoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<PhotoEntity, UUID> {

    List<PhotoEntity> findAllByUserIdAndSyncSeqGreaterThanOrderBySyncSeqAsc(UUID userId, long since);

    List<PhotoEntity> findAllByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    List<PhotoEntity> findAllByUserId(UUID userId);

    /** Scoped by user so one account can never read another's photo by guessing an id. */
    Optional<PhotoEntity> findByIdAndUserId(UUID id, UUID userId);

    /**
     * The live photos of these copies, in the order the strip draws them.
     *
     * One query for a whole shelf rather than one per tile: a profile page asks for up to
     * two hundred copies at once, and the first photo of each is what stands in wherever a
     * copy has no catalogue art. Uploaded-but-not-yet-stored rows are left out — a
     * `storageKey` of null is a photo whose bytes never arrived, and a URL pointing at one
     * would 404 on every tile that used it.
     */
    @Query("""
            SELECT p FROM PhotoEntity p
            WHERE p.userId = :userId
              AND p.copyId IN :copyIds
              AND p.deletedAt IS NULL
              AND p.storageKey IS NOT NULL
            ORDER BY p.sortIndex ASC, p.createdAt ASC
            """)
    List<PhotoEntity> findVisibleForCopies(@Param("userId") UUID userId, @Param("copyIds") Collection<UUID> copyIds);

    /**
     * What this account's live photos weigh, and how many there are.
     *
     * <p>The same predicate the deletion path honours: a tombstone's object is removed when
     * the delete syncs, and a row with no key never had one, so neither is anything the
     * bucket is holding. Summed in the database rather than by loading the rows -- a shelf
     * can have hundreds of photos and the answer is two numbers.
     */
    @Query("""
            SELECT coalesce(sum(p.byteSize), 0) AS bytes, count(p) AS photos
            FROM PhotoEntity p
            WHERE p.userId = :userId
              AND p.deletedAt IS NULL
              AND p.storageKey IS NOT NULL
            """)
    Usage sumLiveBytes(@Param("userId") UUID userId);

    /** The two aggregates above, named, so neither is read out of the wrong column. */
    interface Usage {
        long getBytes();

        long getPhotos();
    }
}
