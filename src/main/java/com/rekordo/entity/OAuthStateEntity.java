package com.rekordo.entity;

import com.rekordo.model.core.OAuthClient;
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

@Entity
@Table(name = "oauth_states")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class OAuthStateEntity {

    @Id
    private String state;

    @Column(nullable = false)
    private String provider;

    /**
     * Which client started the flow. The provider's callback says nothing about who asked,
     * so it has to be remembered from the authorize step or the completed sign-in cannot be
     * delivered to the right place.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthClient client;

    /**
     * SHA-256 of the secret handed to the browser that began this flow, which that browser
     * has to present again for the callback to be completed. Without it the state is a
     * bearer token, and a callback URL an attacker holds is a session anybody can be walked
     * into. Null only for a row that predates the column.
     */
    @Column(name = "binding_hash")
    private String bindingHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set on first use, so a replayed callback cannot mint a second session. */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
