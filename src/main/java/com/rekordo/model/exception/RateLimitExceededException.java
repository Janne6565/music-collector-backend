package com.rekordo.model.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends BaseException {

    public RateLimitExceededException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many metadata requests — slow down and retry shortly.");
    }
}
