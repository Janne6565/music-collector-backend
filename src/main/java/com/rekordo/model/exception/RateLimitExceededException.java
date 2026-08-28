package com.rekordo.model.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends BaseException {

    public RateLimitExceededException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many metadata requests. Slow down and retry shortly.");
    }
}
