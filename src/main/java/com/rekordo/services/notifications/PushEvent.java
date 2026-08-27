package com.rekordo.services.notifications;

import java.util.UUID;

/**
 * Something that happened and might be worth buzzing somebody about.
 *
 * <p>Published rather than sent, and handled after the transaction commits, for the same
 * reason mail is: a push cannot be rolled back. "Milan wants to be friends" sent from a
 * transaction that then fails names a request that does not exist, and nothing takes it back.
 *
 * <p>Only what the copy needs travels in the event. A payload rides through Apple's and
 * Google's servers to reach a phone, so nothing private goes in one.
 */
public sealed interface PushEvent {

    UUID recipientId();

    /**
     * Board 22c's first card, and the only per-event push the design kept: it names a person
     * and waits for an answer. The activity push was written out on a lock screen and killed
     * there — "Anna added 7 copies" asks for nothing and lands at whatever hour Anna shops.
     */
    record FriendRequested(UUID recipientId, String requesterName, String requesterHandle, long requesterCopies)
            implements PushEvent {}
}
