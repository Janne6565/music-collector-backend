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

/** One place a push could arrive, and whether it currently may. */
@Entity
@Table(name = "notification_devices")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class NotificationDeviceEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** The client's own stable id for itself, so re-registering updates rather than piles up. */
    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "push_token", nullable = false)
    private String pushToken;

    @Column(nullable = false)
    private String platform;

    @Column
    private String label;

    /**
     * Set while this device is muted -- a timestamp rather than a flag, because the screen
     * says "muted here since June" and a boolean cannot answer that.
     */
    @Column(name = "muted_at")
    private Instant mutedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
