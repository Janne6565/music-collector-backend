package com.rekordo.repository;

import com.rekordo.entity.SharingSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SharingSettingsRepository extends JpaRepository<SharingSettingsEntity, UUID> {

    List<SharingSettingsEntity> findAllByUserIdIn(Collection<UUID> userIds);
}
