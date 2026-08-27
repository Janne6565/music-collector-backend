package com.rekordo.model.exception;

import org.springframework.http.HttpStatus;

public class ReleaseNotFoundException extends BaseException {

    public ReleaseNotFoundException(String releaseId) {
        // The id is source-qualified, so it already says which catalogue was searched.
        super(HttpStatus.NOT_FOUND, "No release found for " + releaseId);
    }
}
