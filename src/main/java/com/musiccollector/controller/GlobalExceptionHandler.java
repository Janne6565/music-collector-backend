package com.musiccollector.controller;

import com.musiccollector.model.exception.BaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
}
