package com.rekordo.client;

import java.util.Optional;

/**
 * What asking an archive for a cover actually taught us.
 *
 * <p>Three answers rather than two, and the third is the whole point. "The archive holds
 * nothing for this pressing" is a fact worth writing down for good. "The request did not come
 * back" is not — and recording the second as the first is how one throttled minute took the
 * cover off a record permanently: {@code has_cover_art} went to false, the URL stopped being
 * served, and every client that refreshed its catalogue cache dropped the picture too.
 *
 * <p>So an unreachable probe leaves the question open and the next lookup asks again.
 */
public record CoverProbe(byte[] bytes, boolean conclusive) {

    /** The archive answered and handed over the image. */
    public static CoverProbe found(byte[] bytes) {
        return new CoverProbe(bytes, true);
    }

    /** The archive answered and has nothing. A definite no. */
    public static CoverProbe absent() {
        return new CoverProbe(null, true);
    }

    /** Nobody answered: offline, throttled, timed out, or the archive is having a bad minute. */
    public static CoverProbe unreachable() {
        return new CoverProbe(null, false);
    }

    /** Whether there is a cover, which is only meaningful once {@link #conclusive()} is true. */
    public boolean found() {
        return bytes != null;
    }

    /** The bytes to sample, if any came back. */
    public Optional<byte[]> image() {
        return Optional.ofNullable(bytes);
    }
}
