package com.rekordo.model.core;

import java.time.Instant;

/**
 * What the account screen needs to draw the confirmation row (design 21c) and the waiting
 * row of a pending address change (21g).
 *
 * <p>It exists because the row has to survive a reload: "link sent, good for 24 hours" and
 * the resend countdown are facts about the server, and a client that only learned them from
 * its own last button press would forget them the moment the page came back.
 *
 * @param confirmed     whether the address on the account has been proved
 * @param sentAt        when the outstanding link went out, or null if none is outstanding
 * @param expiresAt     when that link stops working
 * @param retryAfter    seconds until another link may be asked for. Zero when one may be
 *                      asked for now — the button is a countdown rather than an error,
 *                      because pressing twice is impatience, not a mistake.
 * @param pendingEmail  the address a change is waiting on, or null when none is
 */
public record EmailConfirmationDto(
        boolean confirmed, Instant sentAt, Instant expiresAt, long retryAfter, String pendingEmail) {}
