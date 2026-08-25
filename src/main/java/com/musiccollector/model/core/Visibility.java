package com.musiccollector.model.core;

/**
 * Who may see one list. Asked separately of the collection and the wishlist, because
 * sharing what you are hunting for is a much smaller decision than opening your shelf.
 */
public enum Visibility {

    /** Nobody but the owner. A friend sees the name and nothing else. */
    ONLY_ME,

    /** The accepted friends, and nobody else. */
    FRIENDS,

    /** A page anyone can open, no account needed. */
    PUBLIC;

    public boolean isAtLeast(Visibility required) {
        return ordinal() >= required.ordinal();
    }
}
