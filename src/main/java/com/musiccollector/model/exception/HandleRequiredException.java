package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

/**
 * Something social was attempted by an account with no handle. Claiming one is the first
 * thing the Friends tab asks for, so this means a client skipped that screen.
 */
public class HandleRequiredException extends BaseException {

    public HandleRequiredException() {
        super(HttpStatus.CONFLICT, "Claim a handle before using Friends.");
    }
}
