package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

public class UpstreamUnavailableException extends BaseException {

    public UpstreamUnavailableException(String upstream, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "Upstream unavailable: " + upstream);
        initCause(cause);
    }
}
