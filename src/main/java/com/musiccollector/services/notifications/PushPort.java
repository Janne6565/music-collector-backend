package com.musiccollector.services.notifications;

import java.util.List;

/** Sending push, so everything above it can be tested without a phone. */
public interface PushPort {

    /**
     * Sends a batch and returns the tokens the service rejected as permanently dead.
     *
     * <p>Dead tokens come back rather than being thrown: a device that has been reinstalled
     * or deleted is not an error, it is a row to forget.
     */
    List<String> send(List<PushMessage> messages);
}
