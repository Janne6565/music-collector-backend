package com.musiccollector.services.mail;

/** Sending mail, so the reset flow can be tested without a mail server. */
public interface MailPort {
    void send(String recipient, String subject, String html, String text);
}
