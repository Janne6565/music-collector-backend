package com.rekordo.repository;

import com.rekordo.entity.NotificationDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeviceRepository extends JpaRepository<NotificationDeviceEntity, UUID> {

    List<NotificationDeviceEntity> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<NotificationDeviceEntity> findByUserIdAndDeviceId(UUID userId, String deviceId);

    /** Every token that may currently be sent to for this account. */
    List<NotificationDeviceEntity> findAllByUserIdAndMutedAtIsNull(UUID userId);

    /** A token Expo has told us is dead belongs to whichever device still carries it. */
    List<NotificationDeviceEntity> findAllByPushToken(String pushToken);
}
