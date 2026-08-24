package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

public class OAuthFailedException extends BaseException {

    public OAuthFailedException(String detail) {
        super(HttpStatus.BAD_REQUEST, detail);
    }
}
