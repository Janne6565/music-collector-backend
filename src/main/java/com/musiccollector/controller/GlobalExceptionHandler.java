package com.musiccollector.controller;

import com.musiccollector.model.exception.BaseException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log4xx = LoggerFactory.getLogger("log4xx");
    private static final Logger log5xx = LoggerFactory.getLogger("log5xx");

    @ExceptionHandler(BaseException.class)
    public ProblemDetail handle(BaseException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log5xx.error("{} — {}", ex.getStatus(), ex.getDetail(), ex);
        } else {
            log4xx.warn("{} — {}", ex.getStatus(), ex.getDetail());
        }
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getDetail());
    }

    /**
     * Raised by {@code @Validated} on query and path parameters — a malformed barcode, a
     * blank search term. A client mistake, so it is logged as one.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handle(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(java.util.stream.Collectors.joining("; "));
        log4xx.warn("400 — {}", detail);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
