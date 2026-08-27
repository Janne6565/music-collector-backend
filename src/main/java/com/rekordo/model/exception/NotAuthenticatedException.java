package com.rekordo.model.exception;

import org.springframework.http.HttpStatus;

public class NotAuthenticatedException extends BaseException {

    public NotAuthenticatedException() {
        super(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    }
}
