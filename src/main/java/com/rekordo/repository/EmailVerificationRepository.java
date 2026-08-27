package com.rekordo.repository;

import com.rekordo.entity.EmailVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationRepository extends JpaRepository<EmailVerificationEntity, UUID> {

    Optional<EmailVerificationEntity> findByTokenHash(String tokenHash);

    Optional<EmailVerificationEntity> findByCancelTokenHash(String cancelTokenHash);

    /**
     * Every link this account has out and could still redeem, newest first.
     *
     * Used both to retire the older one when a new link is issued -- two live links would
     * mean the older mail sometimes works and sometimes does not -- and to describe the
     * waiting state to the account screen after a reload.
     */
    @Query("""
            SELECT v FROM EmailVerificationEntity v
            WHERE v.userId = :userId AND v.usedAt IS NULL
            ORDER BY v.createdAt DESC
            """)
    List<EmailVerificationEntity> findOutstanding(@Param("userId") UUID userId);
}
