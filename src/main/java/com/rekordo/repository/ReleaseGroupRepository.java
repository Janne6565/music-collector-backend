package com.rekordo.repository;

import com.rekordo.entity.ReleaseGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReleaseGroupRepository extends JpaRepository<ReleaseGroupEntity, UUID> {

    Optional<ReleaseGroupEntity> findByExternalId(String externalId);

    /** One query for a screenful of albums, rather than one per row. */
    List<ReleaseGroupEntity> findAllByExternalIdIn(Collection<String> externalIds);
}
