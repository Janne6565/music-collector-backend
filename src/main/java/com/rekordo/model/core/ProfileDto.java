package com.rekordo.model.core;

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
        /**
         * Their picture, or null. Present whatever the three verdicts below say: the
         * picture is account data rather than shelf data, so 27f draws it above a
         * collection that stays locked, and the person who uploaded it was told so.
         */
        String avatarUrl,
        RelationshipDto relationship,
        /**
         * The request waiting for this viewer's answer, or null. Set only for
         * {@link RelationshipDto#REQUEST_RECEIVED}: accepting and declining name the
         * request, while a profile is looked up by handle, so without it the screen that
         * shows the ask has no way to answer it.
         */
        UUID pendingRequestId,
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
