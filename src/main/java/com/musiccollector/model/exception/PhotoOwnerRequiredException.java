package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

/**
 * A photo pictures a copy or a wishlist entry, and exactly one of them.
 *
 * <p>Rejected at the door rather than stored and sorted out later: the bytes are already
 * in object storage by the time the row is written, and an upload nothing can ever
 * reference is an object nothing will ever delete.
 */
public class PhotoOwnerRequiredException extends BaseException {

    public PhotoOwnerRequiredException() {
        super(HttpStatus.BAD_REQUEST, "A photo belongs to either a copy or a wishlist entry, not both and not neither.");
    }
}
