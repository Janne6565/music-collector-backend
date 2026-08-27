package com.rekordo.repository;

import com.rekordo.entity.OAuthStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthStateRepository extends JpaRepository<OAuthStateEntity, String> {}
