package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

public class InvalidResetTokenException extends BaseException {

    public InvalidResetTokenException() {
        // One message for expired, already used, and never existed. Distinguishing them
        // would tell someone holding a guessed token which guesses were close.
        super(HttpStatus.BAD_REQUEST, "That reset link is no longer valid. Request a new one.");
    }
}
