package com.musiccollector.repository;

import com.musiccollector.entity.PhotoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<PhotoEntity, UUID> {

    List<PhotoEntity> findAllByUserIdAndSyncSeqGreaterThanOrderBySyncSeqAsc(UUID userId, long since);

    List<PhotoEntity> findAllByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    /** Scoped by user so one account can never read another's photo by guessing an id. */
    Optional<PhotoEntity> findByIdAndUserId(UUID id, UUID userId);
}
