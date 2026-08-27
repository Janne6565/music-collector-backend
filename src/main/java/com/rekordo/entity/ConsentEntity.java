package com.rekordo.entity;

import com.rekordo.model.core.ConsentDocument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One recorded agreement: this account, this document, this version, this moment.
 *
 * Append-only. Accepting a new version of the terms adds a row rather than updating one --
 * the question a consent record answers is "what did they agree to, and when", and an
 * overwritten row cannot answer it.
 */
@Entity
@Table(name = "user_consents")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ConsentEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentDocument document;

    @Column(nullable = false)
    private String version;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;
}
