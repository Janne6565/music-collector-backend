package com.musiccollector.repository;

import com.musiccollector.entity.EmailVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationRepository extends JpaRepository<EmailVerificationEntity, UUID> {

    Optional<EmailVerificationEntity> findByTokenHash(String tokenHash);
}
