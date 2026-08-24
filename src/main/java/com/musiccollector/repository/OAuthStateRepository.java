package com.musiccollector.repository;

import com.musiccollector.entity.OAuthStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthStateRepository extends JpaRepository<OAuthStateEntity, String> {}
