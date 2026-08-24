package com.musiccollector.repository;

import com.musiccollector.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /** Case-insensitive: e-mail is the login identifier and users do not type consistently. */
    @Query("SELECT u FROM UserEntity u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<UserEntity> findByEmailIgnoreCase(String email);

    @Query("SELECT COUNT(u) > 0 FROM UserEntity u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmailIgnoreCase(String email);
}
