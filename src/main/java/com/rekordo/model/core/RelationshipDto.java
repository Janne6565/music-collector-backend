package com.rekordo.model.core;

/**
 * Where the viewer stands with the person they are looking at. One value drives every
 * button in the design: Add, Requested, Friends, or nothing at all when you are looking
 * at yourself.
 */
public enum RelationshipDto {

    /** The viewer is not signed in, so there is nobody to have a relationship with. */
    ANONYMOUS,

    SELF,

    /** Nothing between them yet — the "Add" button. */
    NONE,

    /** The viewer asked and is waiting — the "Requested" button. */
    REQUEST_SENT,

    /** The other person asked; the viewer can accept or decline. */
    REQUEST_RECEIVED,

    FRIENDS
}
