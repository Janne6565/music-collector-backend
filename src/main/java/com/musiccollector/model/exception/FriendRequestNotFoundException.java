package com.musiccollector.model.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** No request with this id is waiting for this user to answer. */
public class FriendRequestNotFoundException extends BaseException {

    public FriendRequestNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "No pending friend request " + id);
    }
}
