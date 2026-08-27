package com.rekordo.repository;

import com.rekordo.entity.ArtistImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ArtistImageRepository extends JpaRepository<ArtistImageEntity, UUID> {}
