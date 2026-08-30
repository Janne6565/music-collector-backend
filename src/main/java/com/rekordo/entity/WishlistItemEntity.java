package com.rekordo.entity;

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

    /**
     * The pressing the entry was made from, or null when none was picked.
     *
     * The wish is still for the album; this only says which sleeve it was wearing when it
     * was added, so the clients do not have to guess one back out of the mirror.
     */
    @Column(name = "release_id")
    private String releaseId;

    /** Mirrors {@code CopyEntity.pendingBarcode}: a scan sent here before it had a name. */
    @Column(name = "pending_barcode")
    private String pendingBarcode;

    @Column(nullable = false)
    private String title;

    @Column(name = "artist_name", nullable = false)
    private String artistName;

    private Integer year;

    @Column(name = "desired_format")
    private String desiredFormat;

    private String note;

    /** Where the entry sits once the list has been hand-sorted; null while it never has been. */
    @Column(name = "sort_index")
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
