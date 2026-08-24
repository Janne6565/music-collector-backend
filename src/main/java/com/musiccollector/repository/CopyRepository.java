package com.musiccollector.repository;

import com.musiccollector.entity.CopyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CopyRepository extends JpaRepository<CopyEntity, UUID> {

    /** Everything the client has not seen yet, oldest change first. */
    List<CopyEntity> findAllByUserIdAndSyncSeqGreaterThanOrderBySyncSeqAsc(UUID userId, long since);

    List<CopyEntity> findAllByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    @Query(value = "SELECT nextval('copies_sync_seq')", nativeQuery = true)
    long nextSyncSeq();
}
