package com.rekordo.model.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class AvatarNotFoundException extends BaseException {

    public AvatarNotFoundException(UUID userId) {
        // An account with no picture and a user id nobody holds answer the same. The
        // endpoint is open, so a distinguishable answer would turn it into a way to find
        // out which accounts exist.
        super(HttpStatus.NOT_FOUND, "No profile picture for " + userId);
    }
}
