package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedPhotoTypeException extends BaseException {

    public UnsupportedPhotoTypeException(String contentType) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Not an image this app can store: " + contentType);
    }
}
