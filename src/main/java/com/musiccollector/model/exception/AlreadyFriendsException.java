package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

/**
 * A friendship or a pending request already exists between these two, in one direction or
 * the other. Idempotent from the client's point of view — the button was already in the
 * state the tap was trying to reach.
 */
public class AlreadyFriendsException extends BaseException {

    public AlreadyFriendsException(String handle) {
        super(HttpStatus.CONFLICT, "There is already a friendship or request with @" + handle);
    }
}
