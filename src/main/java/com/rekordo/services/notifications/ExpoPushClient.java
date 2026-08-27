package com.rekordo.services.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sends through Expo's push service, which fans out to APNs and FCM.
 *
 * <p>This is why the server holds no Apple key: EAS holds the APNs key, the device hands us
 * an Expo token, and this is an ordinary HTTP call. The trade is that Expo is in the path —
 * acceptable for a shelf-list app, and the payload is written so that nothing private rides
 * through it.
 *
 * <p>Best effort by contract, exactly like the mail client: a failure is logged and
 * swallowed. Nothing a person did should fail because a notification could not go out.
 *
 * <p>The one answer worth reading is <em>DeviceNotRegistered</em>. A phone that was wiped or
 * had the app deleted keeps its row forever otherwise, and every later send pays for it.
 */
public class ExpoPushClient implements PushPort {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushClient.class);

    /** Expo accepts up to 100 messages per request. */
    private static final int BATCH = 100;

    private final RestClient restClient;
    private final String endpoint;

    public ExpoPushClient(RestClient restClient, String endpoint) {
        this.restClient = restClient;
        this.endpoint = endpoint;
    }

    @Override
    public List<String> send(List<PushMessage> messages) {
        List<String> dead = new ArrayList<>();
        for (int from = 0; from < messages.size(); from += BATCH) {
            List<PushMessage> batch = messages.subList(from, Math.min(from + BATCH, messages.size()));
            dead.addAll(sendBatch(batch));
        }
        return dead;
    }

    private List<String> sendBatch(List<PushMessage> batch) {
        List<Map<String, Object>> body = batch.stream()
                .map(message -> Map.<String, Object>of(
                        "to", message.token(),
                        "title", message.title(),
                        "body", message.body(),
                        "data", message.data(),
                        // Quiet by design: this is a shelf list, not a chat. The one place a
                        // sound is warranted is a security notice, and that goes by mail too.
                        "sound", "default"))
                .toList();

        try {
            Response response = restClient
                    .post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Response.class);
            return deadTokens(batch, response);
        } catch (RestClientException e) {
            log.warn("Could not send {} push message(s)", batch.size(), e);
            return List.of();
        }
    }

    private List<String> deadTokens(List<PushMessage> batch, Response response) {
        List<String> dead = new ArrayList<>();
        if (response == null || response.data() == null) {
            return dead;
        }
        // Expo answers positionally, one ticket per message in the order they were sent.
        for (int index = 0; index < response.data().size() && index < batch.size(); index++) {
            Ticket ticket = response.data().get(index);
            if (ticket == null || !"error".equals(ticket.status())) {
                continue;
            }
            String error = ticket.details() == null ? null : ticket.details().get("error");
            if ("DeviceNotRegistered".equals(error)) {
                dead.add(batch.get(index).token());
            } else {
                log.warn("Push rejected: {} ({})", ticket.message(), error);
            }
        }
        return dead;
    }

    record Response(List<Ticket> data) {}

    record Ticket(String status, String message, Map<String, String> details) {}
}
