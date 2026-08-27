package com.rekordo.model.exception;

import org.springframework.http.HttpStatus;

/** No account holds this handle. */
public class ProfileNotFoundException extends BaseException {

    public ProfileNotFoundException(String handle) {
        super(HttpStatus.NOT_FOUND, "No collector goes by @" + handle);
    }
}
