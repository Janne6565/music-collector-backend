package com.rekordo.model.core;

/**
 * Why a copy exists — the client's answer, and the only thing that decides whether it
 * reaches anybody's feed.
 *
 * <p>The server cannot work this out for itself. A sync push is a batch either way: two
 * hundred records from a CSV file and two hundred records typed in over a fortnight arrive
 * through the same endpoint in the same shape. Only the device knows which it was.
 *
 * <p>Absent means silence. A client too old to send this is a client whose intent we do not
 * know, and the safe failure mode for a feed is to say nothing rather than to announce
 * somebody's entire collection.
 */
public enum CopyOrigin {

    /** Added one at a time, by a person. The only origin that reaches the feed. */
    MANUAL,

    /** A CSV import. Silent however many records it carries. */
    CSV_IMPORT,

    /** The batch that uploads a local collection the first time an account signs in. */
    FIRST_SYNC
}
