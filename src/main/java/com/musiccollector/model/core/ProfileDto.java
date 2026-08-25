package com.musiccollector.model.core;

import java.time.Instant;
import java.util.UUID;

/**
 * Somebody's profile as one particular viewer is allowed to see it.
 *
 * <p>The three {@code can*} flags are the server's verdict, not a hint: the collection and
 * wishlist endpoints apply the same rules again, so a client that ignores them learns
 * nothing. They exist so the UI can draw the locked state in 15d without having to ask for
 * a list it will be refused.
 */
public record ProfileDto(
        UUID id,
        String handle,
        String displayName,
        RelationshipDto relationship,
        boolean canSeeCollection,
        boolean canSeeWishlist,
        /** Whether prices ride along with the copies. Off even on a public shelf by default. */
        boolean pricesVisible,
        /**
         * How many copies they keep. Present even when the shelf is closed, because 15d
         * says the number out loud — "Friedhelm keeps 431 copies here" is the invitation.
         */
        long copyCount,
        long wishlistCount,
        /** When the account was made, which is as close to "collecting since" as we know. */
        Instant collectingSince) {}
