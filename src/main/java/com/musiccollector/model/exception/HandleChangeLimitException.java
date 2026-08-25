package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

/**
 * The twice-a-year limit. Not arbitrary friction: a handle is how people find and link to
 * each other, and one that changes freely is one that nobody can rely on.
 */
public class HandleChangeLimitException extends BaseException {

    public HandleChangeLimitException(int allowedPerYear) {
        super(
                HttpStatus.CONFLICT,
                "A handle can be changed %d times a year. Try again later.".formatted(allowedPerYear));
    }
}
