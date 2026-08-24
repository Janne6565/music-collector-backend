package com.musiccollector.repository;

import com.musiccollector.entity.ReleaseGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReleaseGroupRepository extends JpaRepository<ReleaseGroupEntity, UUID> {

    Optional<ReleaseGroupEntity> findByMbid(UUID mbid);
}
