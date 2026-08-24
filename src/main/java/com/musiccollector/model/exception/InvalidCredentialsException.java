package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends BaseException {

    public InvalidCredentialsException() {
        // Deliberately does not say which half was wrong — that would confirm whether an
        // address is registered to anyone who asks.
        super(HttpStatus.UNAUTHORIZED, "That e-mail address and password do not match.");
    }
}
