package com.rekordo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_identities")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class OAuthIdentityEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String provider;

    /** The provider's stable id for the person — not the e-mail, which can change. */
    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
