package com.musiccollector.repository;

import com.musiccollector.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreferenceEntity, NotificationPreferenceEntity.Key> {

    List<NotificationPreferenceEntity> findAllByUserId(UUID userId);
}
