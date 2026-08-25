package com.musiccollector.repository;

import com.musiccollector.entity.ReleaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReleaseRepository extends JpaRepository<ReleaseEntity, UUID> {

    Optional<ReleaseEntity> findByExternalId(String externalId);

    List<ReleaseEntity> findAllByExternalIdIn(Collection<String> externalIds);

    /** Barcode scans check the local mirror before ever calling MusicBrainz. */
    List<ReleaseEntity> findAllByBarcode(String barcode);
}
