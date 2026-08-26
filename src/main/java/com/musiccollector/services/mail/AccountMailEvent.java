package com.musiccollector.services.mail;

import java.time.Instant;

/**
 * Something that happened to an account and is worth a mail.
 *
 * <p>Published rather than sent, so that {@link AccountMailListener} can send it after the
 * transaction commits. Two reasons, and the first one is the one that matters: a mail is not
 * rollback-able. "Your account has been deleted" sent from inside the transaction that then
 * fails is a claim about somebody's data that is simply untrue, and nothing can take it
 * back. The second is that an HTTP call to the mail service has no business holding a
 * database connection open.
 *
 * <p>Everything the mail needs is carried in the event, because by the time it is handled
 * the rows may be gone — which is the whole point for {@link AccountDeleted}.
 */
public sealed interface AccountMailEvent {

    String recipient();

    record PasswordResetRequested(String recipient, String token) implements AccountMailEvent {}

    record EmailConfirmationRequested(String recipient, String token) implements AccountMailEvent {}

    /** The confirmation link for an address the account is moving to. */
    record EmailChangeRequested(String recipient, String token) implements AccountMailEvent {}

    /**
     * The notice to the address being moved away from, carrying the undo.
     *
     * <p>Sent the moment the change is asked for rather than when it lands: if somebody else
     * is at the keyboard, the old mailbox is the only place left to say so.
     */
    record EmailChangeStarted(String recipient, String newEmail, String cancelToken, Instant at)
            implements AccountMailEvent {}

    record PasswordChanged(String recipient, Instant at) implements AccountMailEvent {}

    record SignInMethodLinked(String recipient, String provider, Instant at) implements AccountMailEvent {}

    record AccountDeleted(String recipient, long copies) implements AccountMailEvent {}
}
