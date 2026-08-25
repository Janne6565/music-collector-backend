package com.musiccollector.repository;

import com.musiccollector.entity.OAuthHandoffEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthHandoffRepository extends JpaRepository<OAuthHandoffEntity, String> {}
