package com.rekordo.model.exception;

import org.springframework.http.HttpStatus;

/**
 * Root of every application exception. Carries the HTTP status and the client-facing
 * detail so a single handler in {@code GlobalExceptionHandler} can translate all of them.
 */
public abstract class BaseException extends RuntimeException {

    private final HttpStatus status;
    private final String detail;

    protected BaseException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
        this.detail = detail;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }
}
