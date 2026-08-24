package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

public class StorageUnavailableException extends BaseException {

    public StorageUnavailableException(String operation, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "Photo storage is unavailable (" + operation + ")");
        initCause(cause);
    }
}
