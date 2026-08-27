package com.rekordo.repository;

import com.rekordo.entity.ReleaseTrackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReleaseTrackRepository extends JpaRepository<ReleaseTrackEntity, UUID> {

    List<ReleaseTrackEntity> findByReleaseIdOrderByMediumPositionAscPositionAsc(UUID releaseId);

    void deleteByReleaseId(UUID releaseId);
}
