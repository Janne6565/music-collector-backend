package com.rekordo.model.core;

/**
 * A friendship has only two states worth storing. Declining deletes the row rather than
 * recording a rejection: nobody needs a durable list of the people who said no, and a
 * deleted request is what lets an accidental decline be undone by asking again.
 */
public enum FriendshipStatus {
    PENDING,
    ACCEPTED
}
