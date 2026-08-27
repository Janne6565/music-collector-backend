package com.rekordo.services.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Used when push is switched off. Logs instead of sending, so local development can see what
 * would have arrived without any risk of buzzing a real phone.
 */
public class NoopPushPort implements PushPort {

    private static final Logger log = LoggerFactory.getLogger(NoopPushPort.class);

    @Override
    public List<String> send(List<PushMessage> messages) {
        for (PushMessage message : messages) {
            log.info("Push not enabled; would have sent to {}: \"{}\" — {}", message.token(), message.title(),
                    message.body());
        }
        return List.of();
    }
}
