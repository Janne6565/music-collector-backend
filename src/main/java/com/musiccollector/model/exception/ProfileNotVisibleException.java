package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

/**
 * The list exists but this viewer may not have it.
 *
 * <p>403 rather than 404: the profile itself is not a secret — 15d shows a locked shelf
 * with a name on it and a way to ask — so pretending the person does not exist would break
 * the screen the design actually draws.
 */
public class ProfileNotVisibleException extends BaseException {

    public ProfileNotVisibleException(String handle) {
        super(HttpStatus.FORBIDDEN, "@%s does not share this list with you.".formatted(handle));
    }
}
