package com.rekordo.services.notifications;

import com.rekordo.entity.UserEntity;
import com.rekordo.model.core.NotificationCategory;
import com.rekordo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * Turns an event into a push, once the thing it describes has actually committed.
 *
 * <p>Two gates, in this order, and both matter:
 *
 * <ol>
 *   <li><b>The account's grid</b> — does this category buzz at all for this person.
 *   <li><b>The device's mute</b> — which of their devices may. A muted phone is dropped
 *       here rather than sent to and ignored, because "sent" is what Expo would report.
 * </ol>
 *
 * <p>A token the push service says is dead is forgotten on the spot. A phone that was wiped
 * or had the app deleted would otherwise keep its row forever, and every later send pays.
 */
@Component
@RequiredArgsConstructor
public class PushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);

    private final UserRepository userRepository;
    private final NotificationPreferenceService preferenceService;
    private final NotificationDeviceService deviceService;
    private final PushPort pushPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PushEvent event) {
        UserEntity recipient = userRepository.findById(event.recipientId()).orElse(null);
        if (recipient == null) {
            return;
        }

        switch (event) {
            case PushEvent.FriendRequested e -> send(
                    recipient,
                    NotificationCategory.FRIEND_REQUEST,
                    // 22c: iOS gives this roughly one line, so the person's name goes first
                    // and nothing else competes with it.
                    "%s wants to be friends".formatted(e.requesterName()),
                    "@%s · %s".formatted(e.requesterHandle(), copies(e.requesterCopies())),
                    Map.of("type", "FRIEND_REQUEST", "handle", e.requesterHandle()));
        }
    }

    private void send(UserEntity recipient, NotificationCategory category, String title, String body,
            Map<String, String> data) {
        if (!preferenceService.pushEnabled(recipient, category)) {
            log.debug("{} push is off for user {}", category, recipient.getId());
            return;
        }
        List<String> tokens = deviceService.reachableTokens(recipient.getId());
        if (tokens.isEmpty()) {
            return;
        }

        List<PushMessage> messages =
                tokens.stream().map(token -> new PushMessage(token, title, body, data)).toList();
        pushPort.send(messages).forEach(deviceService::forget);
    }

    private static String copies(long count) {
        return count == 1 ? "1 copy" : count + " copies";
    }
}
