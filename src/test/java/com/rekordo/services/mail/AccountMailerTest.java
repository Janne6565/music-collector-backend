package com.rekordo.services.mail;

import com.rekordo.configuration.MailProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** The six mails, checked for the promises they must not make. */
@ExtendWith(MockitoExtension.class)
class AccountMailerTest {

    @Mock private MailPort mailPort;

    private final MailTemplate template =
            new MailTemplate(new MailProperties("http://mail", "key", "https://music.example"));

    private AccountMailer mailer() {
        return new AccountMailer(template, mailPort);
    }

    private String htmlSentTo(String recipient) {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailPort).send(eq(recipient), any(), html.capture(), any());
        return html.getValue();
    }

    @Test
    void theResetLinkCarriesTheToken() {
        mailer().passwordReset("jonas@example.test", "tok-123");

        assertThat(htmlSentTo("jonas@example.test")).contains("https://music.example/reset?token=tok-123");
    }

    @Test
    void theConfirmationLinkPointsAtTheConfirmScreen() {
        mailer().confirmEmail("jonas@example.test", "tok-123");

        assertThat(htmlSentTo("jonas@example.test")).contains("https://music.example/confirm/tok-123");
    }

    @Test
    void theSecurityNoticeHasNothingToPress() {
        mailer().passwordChanged("jonas@example.test", Instant.parse("2026-08-26T12:02:00Z"));
        String html = htmlSentTo("jonas@example.test");

        // Board 1d's whole point: a notice that looks like it wants a click teaches people
        // to click the one a phishing copy of it would put there.
        assertThat(html).doesNotContain("<td class=\"mc-btn-cell\"");
        assertThat(html).doesNotContain("Or paste this into your browser:");
        // And the escape is a link to the form, never a working one-click reset token.
        assertThat(html).contains("https://music.example/forgot");
        assertThat(html).contains("26 August 2026, 14:02 CEST");
    }

    @Test
    void theLinkedProviderIsNamed() {
        mailer().signInMethodLinked("jonas@example.test", "google", Instant.parse("2026-08-26T12:02:00Z"));

        assertThat(htmlSentTo("jonas@example.test")).contains("Google is now linked");
    }

    @Test
    void theGoodbyeCountsWhatWentAndPromisesNoBackup() {
        mailer().accountDeleted("jonas@example.test", 240);
        String html = htmlSentTo("jonas@example.test");

        assertThat(html).contains("240 copies");
        // There are no backups. The deck drew a line about them rolling off in 30 days, and
        // shipping it would have been a claim about somebody's data that is simply untrue.
        assertThat(html).doesNotContainIgnoringCase("backup");
    }

    @Test
    void anEmptyShelfIsNotEnumerated() {
        mailer().accountDeleted("jonas@example.test", 0);

        assertThat(htmlSentTo("jonas@example.test")).doesNotContain("0 copies").contains("everything it held.");
    }

    @Test
    void oneCopyIsNotOneCopies() {
        mailer().accountDeleted("jonas@example.test", 1);

        assertThat(htmlSentTo("jonas@example.test")).contains("1 copy,").doesNotContain("1 copies");
    }
}
