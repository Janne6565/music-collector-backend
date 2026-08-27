package com.musiccollector.services.mail;

import java.util.ArrayList;
import java.util.List;

/**
 * What a transactional mail says, in the pieces the shell knows how to set.
 *
 * <p>Deliberately not HTML. Every mail is the same 600px table with the same rhythm, and the
 * only thing that varies between them is which of these slots are filled — so a new mail is
 * a paragraph and a button, never a second copy of the shell. {@link MailTemplate} turns one
 * of these into both the HTML and the plain-text twin that ships beside it.
 *
 * @param facts   a small monospace block under the body for the raw particulars of an event
 *                — a timestamp, a provider name. Empty for mails that state no facts.
 * @param action  the single primary button, or null for a mail with nothing to click. There
 *                is never a second button: the design gives the runner-up a text link.
 * @param rows    a list of records rather than a paragraph (design 22f), set as plain rows
 *                so a mail client cannot break them. Empty for every other mail.
 * @param closing one italic serif line at the very end, used only by the goodbye.
 * @param footerReason why this mail arrived, in one sentence. Never null — a transactional
 *                mail that cannot say why it was sent should not be sent.
 */
public record MailContent(
        String subject,
        String headline,
        List<String> paragraphs,
        List<String> facts,
        List<Row> rows,
        Action action,
        Note note,
        String closing,
        String footerReason,
        Unsubscribe unsubscribe) {

    /** A bulletproof button. The URL is also printed in full below it, by the shell. */
    public record Action(String label, String url) {}

    /** One record in a digest: what it is on top, the particulars underneath. */
    public record Row(String title, String detail) {}

    /**
     * The footer slot the shell has always had and nothing used until the digest (22f).
     *
     * <p>{@code what} names the one category being switched off, and the copy says so, because
     * a link that also silenced security notices would be a trap.
     */
    public record Unsubscribe(String label, String url, String what) {}

    /**
     * The flat-toned block that holds caveats, expiry and the way out.
     *
     * @param heading  bolder first line, or null when the note is a single voice. A heading
     *                 is what raises a note from a caveat to a warning, so it is the one
     *                 knob that separates the security notices from the rest.
     * @param linkUrl  printed under {@code linkLabel} in monospace when {@code showLinkUrl},
     *                 for the clients that strip anchors out of a text link.
     */
    public record Note(String heading, String text, String linkLabel, String linkUrl, boolean showLinkUrl) {}

    public static Builder builder(String subject, String headline) {
        return new Builder(subject, headline);
    }

    public static final class Builder {

        private final String subject;
        private final String headline;
        private final List<String> paragraphs = new ArrayList<>();
        private final List<String> facts = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();
        private Action action;
        private Note note;
        private String closing;
        private String footerReason;
        private Unsubscribe unsubscribe;

        private Builder(String subject, String headline) {
            this.subject = subject;
            this.headline = headline;
        }

        public Builder paragraph(String text) {
            paragraphs.add(text);
            return this;
        }

        public Builder fact(String text) {
            facts.add(text);
            return this;
        }

        public Builder row(String title, String detail) {
            rows.add(new Row(title, detail));
            return this;
        }

        public Builder unsubscribe(String label, String url, String what) {
            this.unsubscribe = new Unsubscribe(label, url, what);
            return this;
        }

        public Builder action(String label, String url) {
            this.action = new Action(label, url);
            return this;
        }

        public Builder note(String text) {
            this.note = new Note(null, text, null, null, false);
            return this;
        }

        public Builder note(String heading, String text, String linkLabel, String linkUrl, boolean showLinkUrl) {
            this.note = new Note(heading, text, linkLabel, linkUrl, showLinkUrl);
            return this;
        }

        public Builder closing(String text) {
            this.closing = text;
            return this;
        }

        public Builder reason(String text) {
            this.footerReason = text;
            return this;
        }

        public MailContent build() {
            if (footerReason == null) {
                throw new IllegalStateException("A transactional mail must say why it arrived");
            }
            return new MailContent(
                    subject,
                    headline,
                    List.copyOf(paragraphs),
                    List.copyOf(facts),
                    List.copyOf(rows),
                    action,
                    note,
                    closing,
                    footerReason,
                    unsubscribe);
        }
    }
}
