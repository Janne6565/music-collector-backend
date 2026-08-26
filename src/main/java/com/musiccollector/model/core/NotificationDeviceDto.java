package com.musiccollector.model.core;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the device list on 22a.
 *
 * <p>The push token is deliberately not in here. It addresses somebody's phone, and the only
 * thing that ever needs it is a send.
 *
 * @param mutedAt when this device was muted, or null while it may buzz. A timestamp because
 *                the screen says "muted here since June", which a flag cannot answer.
 * @param current whether this is the device asking — the list says "this iPhone" for one row
 *                and names the rest, and only the server knows which is which.
 */
public record NotificationDeviceDto(
        UUID id, String platform, String label, Instant mutedAt, Instant createdAt, boolean current) {}
