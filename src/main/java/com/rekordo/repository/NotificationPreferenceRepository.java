package com.rekordo.repository;

import com.rekordo.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreferenceEntity, NotificationPreferenceEntity.Key> {

    List<NotificationPreferenceEntity> findAllByUserId(UUID userId);
}
