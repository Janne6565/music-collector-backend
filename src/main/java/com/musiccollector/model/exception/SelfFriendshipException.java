package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

/** Asking yourself to be friends. Caught here so the database constraint never has to. */
public class SelfFriendshipException extends BaseException {

    public SelfFriendshipException() {
        super(HttpStatus.BAD_REQUEST, "You are already yourself.");
    }
}
