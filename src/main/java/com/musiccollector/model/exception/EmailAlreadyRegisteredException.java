package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends BaseException {

    public EmailAlreadyRegisteredException() {
        super(HttpStatus.CONFLICT, "That e-mail address is already registered.");
    }
}
