package com.rekordo.model.core;

/**
 * Why a release has no tracklist, when the answer is permanent (design 26e).
 *
 * <p>These are dead ends, not failures: each one is drawn as a labelled absence with no
 * retry, because retrying cannot change any of them. The one case that <em>is</em> worth
 * retrying — the catalogue not answering — is a 502 instead, and never a reason here.
 */
public enum TracklistUnavailableReason {

    /** A Discogs pressing. Discogs gives the app a count through search, and no lookup by id. */
    DISCOGS,

    /** Nothing in either catalogue matches this id, so there is nothing to read titles from. */
    NOT_IN_CATALOGUE
}
