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

    record PasswordChanged(String recipient, Instant at) implements AccountMailEvent {}

    record SignInMethodLinked(String recipient, String provider, Instant at) implements AccountMailEvent {}

    record AccountDeleted(String recipient, long copies) implements AccountMailEvent {}
}
