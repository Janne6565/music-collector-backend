package com.rekordo.model.exception;

import org.springframework.http.HttpStatus;

public class PhotoTooLargeException extends BaseException {

    public PhotoTooLargeException(long limitBytes) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "Photos must be smaller than " + (limitBytes / 1_000_000) + " MB.");
    }
}
