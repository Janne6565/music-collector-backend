package com.rekordo.repository;

import com.rekordo.entity.UnsubscribeTokenEntity;
import com.rekordo.model.core.NotificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UnsubscribeTokenRepository extends JpaRepository<UnsubscribeTokenEntity, UUID> {

    Optional<UnsubscribeTokenEntity> findByTokenHash(String tokenHash);

    Optional<UnsubscribeTokenEntity> findByUserIdAndCategory(UUID userId, NotificationCategory category);
}
