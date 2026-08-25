package com.musiccollector.repository;

import com.musiccollector.entity.OAuthIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentityEntity, UUID> {

    Optional<OAuthIdentityEntity> findByProviderAndProviderSubject(String provider, String providerSubject);

    List<OAuthIdentityEntity> findAllByUserId(UUID userId);
}
