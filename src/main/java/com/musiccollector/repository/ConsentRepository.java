package com.musiccollector.repository;

import com.musiccollector.entity.ConsentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsentRepository extends JpaRepository<ConsentEntity, UUID> {

    /** Newest first: the current agreement is the one at the top, the rest is its history. */
    List<ConsentEntity> findAllByUserIdOrderByAcceptedAtDesc(UUID userId);
}
