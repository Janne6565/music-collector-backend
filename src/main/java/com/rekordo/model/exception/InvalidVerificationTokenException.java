package com.rekordo.model.exception;

import org.springframework.http.HttpStatus;

public class InvalidVerificationTokenException extends BaseException {

    public InvalidVerificationTokenException() {
        // One message for expired, already used, and never existed -- the same reasoning as
        // InvalidResetTokenException.
        super(HttpStatus.BAD_REQUEST, "That confirmation link is no longer valid. Ask for a new one.");
    }
}
