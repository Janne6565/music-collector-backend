package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class PhotoNotFoundException extends BaseException {

    public PhotoNotFoundException(UUID id) {
        // Deliberately the same answer whether the photo does not exist or belongs to
        // somebody else — otherwise this endpoint reports which ids are real.
        super(HttpStatus.NOT_FOUND, "No such photo: " + id);
    }
}
