package com.musiccollector.repository;

import com.musiccollector.entity.WishlistItemEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WishlistItemRepository extends JpaRepository<WishlistItemEntity, UUID> {

    List<WishlistItemEntity> findAllByUserIdAndSyncSeqGreaterThanOrderBySyncSeqAsc(UUID userId, long since);

    List<WishlistItemEntity> findAllByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    @Query("""
            SELECT w FROM WishlistItemEntity w
            WHERE w.userId = :userId AND w.deletedAt IS NULL
            ORDER BY w.createdAt DESC
            """)
    List<WishlistItemEntity> findVisible(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT COUNT(w) FROM WishlistItemEntity w WHERE w.userId = :userId AND w.deletedAt IS NULL")
    long countVisible(@Param("userId") UUID userId);
}
