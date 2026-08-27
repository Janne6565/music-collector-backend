package com.musiccollector.repository;

import com.musiccollector.entity.UnsubscribeTokenEntity;
import com.musiccollector.model.core.NotificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UnsubscribeTokenRepository extends JpaRepository<UnsubscribeTokenEntity, UUID> {

    Optional<UnsubscribeTokenEntity> findByTokenHash(String tokenHash);

    Optional<UnsubscribeTokenEntity> findByUserIdAndCategory(UUID userId, NotificationCategory category);
}
