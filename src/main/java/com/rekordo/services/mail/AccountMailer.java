package com.rekordo.services.mail;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The account mails, one method each, as drawn on boards 1b–1f of the deck.
 *
 * <p>The copy lives here rather than at each call site so that the six mails can be read
 * next to each other — which is the only way to notice that two of them are making the same
 * promise in different words.
 *
 * <p>Three lines the deck drew are deliberately not shipped, because they would have been
 * false: the sign-in location on the password notice (there is no geo-IP and a parsed
 * User-Agent is invented precision), the "encrypted backups roll off within 30 days" line on
 * the goodbye (there are no backups yet), and the claim that an unconfirmed address keeps a
 * collection on one device (sync is not gated on confirmation). A mail is the copy of a
 * claim a person keeps.
 *
 * <p>Everything is English for now. The app is bilingual but {@code users} carries no
 * language, so there is nothing to pick a translation with — see the note in the plan.
 */
@Service
public class AccountMailer {

    /** The deck's format. Berlin because that is where the operator is, not the reader. */
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm z", Locale.ENGLISH).withZone(ZoneId.of("Europe/Berlin"));

    private final MailTemplate template;
    private final MailPort mailPort;

    public AccountMailer(MailTemplate template, MailPort mailPort) {
        this.template = template;
        this.mailPort = mailPort;
    }

    /** Board 1b. The load-bearing mail: one reason, one action, the caveats in the note. */
    public void passwordReset(String recipient, String token) {
        send(
                recipient,
                MailContent.builder("Reset your Rekordo password", "Reset your password")
                        .paragraph(("You asked to set a new password for the Rekordo account belonging to %s. "
                                        + "Choose a new one and your collection stays exactly where it is.")
                                .formatted(recipient))
                        .action("Choose a new password", template.publicUrl() + "/reset?token=" + token)
                        .note("The link works once and expires an hour after it was sent. If this wasn’t you, "
                                + "nothing has changed — your password still works and no one can reach the "
                                + "account without this mail.")
                        .reason("You are receiving this because someone requested a password reset for this address.")
                        .build());
    }

    /** Board 1c. */
    public void confirmEmail(String recipient, String token) {
        send(
                recipient,
                MailContent.builder("Confirm your e-mail address", "Confirm your e-mail address")
                        .paragraph(("One click and %s is confirmed. After that a password reset can find its way "
                                        + "back to you, and we can reach you if a sign-in ever needs checking.")
                                .formatted(recipient))
                        .action("Confirm this address", template.publicUrl() + "/confirm/" + token)
                        .note("The link expires in 24 hours. If it runs out, ask for a new one on your Account "
                                + "screen. Your collection, wishlist and photos sync either way — confirming "
                                + "protects the account rather than unlocking it.")
                        .reason("You are receiving this because this address was entered for a Rekordo "
                                + "account.")
                        .build());
    }

    /**
     * The same link, for an address the account is moving to (design 21g).
     *
     * <p>It says what the old address is still doing, because the one thing somebody worries
     * about mid-change is whether they have just locked themselves out.
     */
    public void confirmNewAddress(String recipient, String token) {
        send(
                recipient,
                MailContent.builder("Confirm your new e-mail address", "Confirm your new address")
                        .paragraph(("Somebody asked to move a Rekordo account to %s. One click and it is "
                                        + "yours — until then the old address goes on working, so nothing is lost "
                                        + "if this was not you.")
                                .formatted(recipient))
                        .action("Confirm this address", template.publicUrl() + "/confirm/" + token)
                        .note("The link expires in 24 hours, and the change lapses on its own if nobody answers. "
                                + "The address it is moving away from has been told, and can cancel it.")
                        .reason("You are receiving this because a Rekordo account is being moved to this "
                                + "address.")
                        .build());
    }

    /**
     * The notice to the address being moved away from (design 21g).
     *
     * <p>In the security family and shaped like one: no button, and the undo is a text link.
     * It goes out when the change is <em>asked for</em>, not when it lands — if somebody else
     * is at the keyboard, this mailbox is the only place left to say so, and by the time the
     * change went through it would be too late to be a warning.
     */
    public void emailChangeStarted(String recipient, String newEmail, String cancelToken, Instant at) {
        send(
                recipient,
                MailContent.builder("Your Rekordo address is being changed", "Your address is being changed")
                        .paragraph(("Somebody asked to move this Rekordo account to %s. Nothing has happened "
                                        + "yet: %s goes on signing you in, and goes on receiving resets, until the "
                                        + "new address answers its own link.")
                                .formatted(newEmail, recipient))
                        .fact("Requested %s".formatted(STAMP.format(at)))
                        .note(
                                "If you didn’t ask for this",
                                "Cancel it. That puts the account back on this address and signs out every device, "
                                        + "including whoever asked. The link keeps working for a day after the "
                                        + "change goes through, so it is not too late if you read this tomorrow.",
                                "Cancel this change",
                                template.publicUrl() + "/email/cancel/" + cancelToken,
                                true)
                        .reason("You are receiving this because a change was requested for this address. Security "
                                + "notices cannot be switched off.")
                        .build());
    }

    /**
     * Board 1d. No button, no alarm box: the mail must not be mistaken for one asking you to
     * act, or it teaches people to click the thing a phishing copy of it would put there.
     *
     * <p>Which is also why the escape link goes to the forgot-password form rather than
     * carrying a one-click token. An unsolicited notice that contains a working reset link is
     * the exact shape the reset flow is careful not to have.
     */
    public void passwordChanged(String recipient, Instant at) {
        send(
                recipient,
                MailContent.builder("Your Rekordo password was changed", "Your password was changed")
                        .paragraph(("The password for %s was changed. Every signed-in device was signed out, so the "
                                        + "app will ask for the new password the next time you open it. Your "
                                        + "collection is untouched.")
                                .formatted(recipient))
                        .fact(STAMP.format(at))
                        .note(
                                "If you didn’t change it",
                                "Reset the password yourself — that locks out anything still holding the old one "
                                        + "and signs every device out again.",
                                "This wasn’t me — secure my account",
                                template.publicUrl() + "/forgot",
                                true)
                        .reason("You are receiving this because your Rekordo password changed. Security "
                                + "notices cannot be switched off.")
                        .build());
    }

    /** Board 1e — the same family as {@link #passwordChanged} one notch down. */
    public void signInMethodLinked(String recipient, String provider, Instant at) {
        String name = displayName(provider);
        send(
                recipient,
                MailContent.builder("A new sign-in method was linked", "A new sign-in method was linked")
                        .paragraph(("%s is now linked to %s. You can sign in with it instead of typing a password. "
                                        + "The account, the collection and the wishlist are unchanged.")
                                .formatted(name, recipient))
                        .fact("%s · linked %s".formatted(name, STAMP.format(at)))
                        .note(
                                null,
                                "Sign-in methods are listed on your Account screen. If you didn’t link this one, "
                                        + "change your password straight away — whoever did can otherwise sign in "
                                        + "without it.",
                                "This wasn’t me",
                                template.publicUrl() + "/forgot",
                                false)
                        .reason("You are receiving this because a sign-in method was added to your account.")
                        .build());
    }

    /** Board 1f — the one mail with nothing to click. */
    public void accountDeleted(String recipient, long copies) {
        send(
                recipient,
                MailContent.builder("Your Rekordo account has been deleted", "Your account has been deleted")
                        .paragraph(copies == 0
                                // An empty shelf has nothing to enumerate, and "0 copies, the wishlist, the
                                // sleeve photos" reads like a bug rather than a goodbye.
                                ? ("%s is gone, and so is everything it held. Nothing is archived and nothing "
                                                + "is kept for later.")
                                        .formatted(recipient)
                                : ("%s is gone, and so is everything it held: %s, the wishlist, the sleeve "
                                                + "photos and the friends list. Nothing is archived and nothing "
                                                + "is kept for later.")
                                        .formatted(recipient, copies == 1 ? "1 copy" : copies + " copies"))
                        .note("The rows and the photo files went as the account did, not into a queue to be tidied "
                                + "up later. This address is free to sign up with again whenever you like — you "
                                + "would start from an empty shelf.")
                        .closing("Thank you for keeping your records with us.")
                        .reason("You are receiving this because the account for this address was deleted. It is the "
                                + "last mail we send you.")
                        .build());
    }

    private void send(String recipient, MailContent content) {
        RenderedMail mail = template.render(content);
        mailPort.send(recipient, mail.subject(), mail.html(), mail.text());
    }

    private static String displayName(String provider) {
        return switch (provider) {
            case "google" -> "Google";
            case "apple" -> "Apple";
            default -> provider;
        };
    }
}
