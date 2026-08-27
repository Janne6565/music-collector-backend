package com.rekordo.services.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when no API key is configured. Logs instead of sending, so local development can
 * follow a reset link without a mail server and without any risk of mailing a real person.
 */
public class NoopMailPort implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(NoopMailPort.class);

    @Override
    public void send(String recipient, String subject, String html, String text) {
        log.info("Mail not configured; would have sent \"{}\" to {}:\n{}", subject, recipient, text);
    }
}
