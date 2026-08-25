package com.musiccollector.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "wishlist_items")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class WishlistItemEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "album_id", nullable = false)
    private String albumId;

    @Column(nullable = false)
    private String title;

    @Column(name = "artist_name", nullable = false)
    private String artistName;

    private Integer year;

    @Column(name = "desired_format")
    private String desiredFormat;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "deleted_at")
    private Long deletedAt;

    @Column(name = "field_clocks", nullable = false)
    private String fieldClocks;

    @Column(name = "sync_seq", nullable = false)
    private Long syncSeq;
}
