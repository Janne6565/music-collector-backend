package com.musiccollector.repository;

import com.musiccollector.entity.WishlistItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WishlistItemRepository extends JpaRepository<WishlistItemEntity, UUID> {

    List<WishlistItemEntity> findAllByUserIdAndSyncSeqGreaterThanOrderBySyncSeqAsc(UUID userId, long since);

    List<WishlistItemEntity> findAllByUserIdAndIdIn(UUID userId, Collection<UUID> ids);
}
