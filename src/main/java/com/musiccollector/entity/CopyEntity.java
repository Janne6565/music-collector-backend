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
@Table(name = "copies")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class CopyEntity {

    /** Client-generated, so a copy created offline keeps its identity when it syncs. */
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "release_mbid", nullable = false)
    private String releaseMbid;

    @Column(name = "condition")
    private String condition;

    @Column(name = "price_paid_cents")
    private Integer pricePaidCents;

    @Column(nullable = false)
    private String currency;

    @Column(name = "purchased_on")
    private String purchasedOn;

    @Column(name = "purchased_at")
    private String purchasedAt;

    private String notes;

    @Column(name = "notes_conflict")
    private String notesConflict;

    private Integer rating;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "deleted_at")
    private Long deletedAt;

    /** Encoded HLC per field, as JSON. Never queried into, so stored as text. */
    @Column(name = "field_clocks", nullable = false)
    private String fieldClocks;

    @Column(name = "sync_seq", nullable = false)
    private Long syncSeq;
}
