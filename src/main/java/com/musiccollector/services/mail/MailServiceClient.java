package com.musiccollector.services.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends through the house Mail Service ({@code POST {baseUrl}/api/v1/send}) with a scoped
 * API key as a bearer token.
 *
 * <p>Best effort by contract: a failure is logged and swallowed. A password reset must not
 * report failure to the caller because the mail queue hiccuped — and it must certainly not
 * reveal, by failing differently, whether the address exists.
 */
public class MailServiceClient implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(MailServiceClient.class);

    private final RestClient restClient;
    private final String sendUri;
    private final String apiKey;

    public MailServiceClient(RestClient restClient, String baseUrl, String apiKey) {
        this.restClient = restClient;
        this.sendUri = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                + "/api/v1/send";
        this.apiKey = apiKey;
    }

    @Override
    public void send(String recipient, String subject, String html, String text) {
        try {
            restClient
                    .post()
                    .uri(sendUri)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SendRequest(recipient, subject, html, true, text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Could not send \"{}\" to {}", subject, recipient, e);
        }
    }

    record SendRequest(String recipient, String subject, String body, boolean enableHtml, String textBody) {}
}
