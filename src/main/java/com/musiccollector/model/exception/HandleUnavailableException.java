package com.musiccollector.model.exception;

import com.musiccollector.model.core.HandleAvailabilityDto;
import org.springframework.http.HttpStatus;

/** A handle that cannot be claimed: malformed, taken, or one the app keeps for itself. */
public class HandleUnavailableException extends BaseException {

    private final HandleAvailabilityDto.Reason reason;

    public HandleUnavailableException(String handle, HandleAvailabilityDto.Reason reason) {
        super(HttpStatus.CONFLICT, "Handle @%s is not available: %s".formatted(handle, reason));
        this.reason = reason;
    }

    public HandleAvailabilityDto.Reason getReason() {
        return reason;
    }
}
