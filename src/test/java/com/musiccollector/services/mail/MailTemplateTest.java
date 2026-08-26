package com.musiccollector.services.mail;

import com.musiccollector.configuration.MailProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shell, tested against the constraints it exists to satisfy rather than against its
 * markup. Every assertion here is something a mail client would otherwise break silently --
 * a blocked image, a stripped anchor, an unreadable dark ground -- in a message nobody can
 * take back.
 */
class MailTemplateTest {

    private final MailTemplate template = new MailTemplate(new MailProperties("http://mail", "key", "https://music.example/"));

    private RenderedMail sample() {
        return template.render(MailContent.builder("Subject", "A headline")
                .paragraph("A body paragraph.")
                .action("Do the thing", "https://music.example/reset?token=abc123")
                .note("A caveat.")
                .reason("You are receiving this because something happened.")
                .build());
    }

    @Test
    void carriesNoImages() {
        // Gmail blocks remote images by default, so anything that carries meaning has to be
        // set type. A wordmark that arrives as a broken-image icon is worse than no wordmark.
        assertThat(sample().html()).doesNotContain("<img");
    }

    @Test
    void printsEveryDestinationTwice() {
        String html = sample().html();
        // Once as the button's href, once as text a person can copy when the anchor has been
        // rewritten or stripped.
        assertThat(html).contains("href=\"https://music.example/reset?token=abc123\"");
        assertThat(html).contains("Or paste this into your browser:");
        assertThat(html.split("https://music.example/reset\\?token=abc123", -1)).hasSizeGreaterThan(2);
    }

    @Test
    void namesTheFallbackFontsBesideTheWebfonts() {
        // The webfonts do not load in most clients; the design has to survive that.
        assertThat(sample().html()).contains("Georgia,serif").contains("system-ui,sans-serif");
    }

    @Test
    void carriesTheNarrowAndDarkVariants() {
        String html = sample().html();
        assertThat(html).contains("@media only screen and (max-width:620px)");
        assertThat(html).contains("@media (prefers-color-scheme:dark)");
        // Both have to beat an inline style, which is the only reason they are !important.
        assertThat(html).contains("background:#100e0c !important");
    }

    @Test
    void namesTheOperatorAndLinksAllThreeDocuments() {
        String html = sample().html();
        assertThat(html).contains("Janne Keipert · Marchlewskistraße 102 · 10243 Berlin");
        assertThat(html).contains("https://music.example/legal/impressum");
        assertThat(html).contains("https://music.example/legal/datenschutz");
        assertThat(html).contains("https://music.example/legal/nutzungsbedingungen");
    }

    @Test
    void trimsTheTrailingSlashOffThePublicUrl() {
        // Configured with one above; a link built naively would carry a double slash.
        assertThat(sample().html()).doesNotContain("music.example//");
    }

    @Test
    void escapesWhateverSomebodyTyped() {
        RenderedMail mail = template.render(MailContent.builder("Subject", "A headline")
                .paragraph("Sent to <script>alert(1)</script>@example.test")
                .reason("Because.")
                .build());

        assertThat(mail.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void wrapsThePlainTextTwinAndLeavesUrlsWhole() {
        String text = sample().text();
        // Prose only: a URL is never folded to fit, which is the point of the next assertion.
        assertThat(text.lines().filter(line -> !line.contains("http")))
                .allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(62));
        // Alone on its line, so no client folds it in half and breaks the link.
        assertThat(text.lines()).contains("https://music.example/reset?token=abc123");
    }

    @Test
    void spellsTheLegalBlockOutInPlainText() {
        // There are no anchors here to hide the provider in, and § 5 DDG still applies.
        assertThat(sample().text()).contains("Janne Keipert").contains("10243 Berlin").contains("Impressum:");
    }

    @Test
    void foldsTypographicMarksForAMonospaceColumn() {
        RenderedMail mail = template.render(MailContent.builder("Subject", "A headline")
                .paragraph("It wasn’t you — nothing changed.")
                .reason("Because.")
                .build());

        assertThat(mail.text()).contains("It wasn't you - nothing changed.");
        assertThat(mail.html()).contains("wasn’t");
    }
}
