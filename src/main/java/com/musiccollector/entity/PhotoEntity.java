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
@Table(name = "photos")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class PhotoEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** The copy this pictures, or null when it belongs to a wishlist entry instead. */
    @Column(name = "copy_id")
    private UUID copyId;

    /** The wishlist entry this pictures. Exactly one of the two owners is set. */
    @Column(name = "wish_id")
    private UUID wishId;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private Long byteSize;

    @Column(name = "sort_index", nullable = false)
    private Integer sortIndex;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "deleted_at")
    private Long deletedAt;

    @Column(name = "field_clocks", nullable = false)
    private String fieldClocks;

    @Column(name = "sync_seq", nullable = false)
    private Long syncSeq;
}
