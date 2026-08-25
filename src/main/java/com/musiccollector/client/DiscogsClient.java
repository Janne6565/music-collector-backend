package com.musiccollector.client;

import com.musiccollector.configuration.DiscogsProperties;
import com.musiccollector.model.exception.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Talks to the Discogs database.
 *
 * <p>Every call is paced. Discogs publishes its quota per minute — 25 anonymous, 60 with a
 * token — and answers 429 rather than queueing, so exceeding it is a failed search, not a
 * slow one.
 */
@Component
public class DiscogsClient {

    private static final Logger log = LoggerFactory.getLogger(DiscogsClient.class);

    private final RestClient restClient;
    private final UpstreamPacer pacer;
    private final DiscogsProperties properties;

    public DiscogsClient(RestClient discogsRestClient, DiscogsProperties properties) {
        this.restClient = discogsRestClient;
        this.properties = properties;
        this.pacer = UpstreamPacer.perMinute(properties.requestsPerMinute());
        if (!properties.authenticated()) {
            log.warn(
                    "Discogs has no token: the quota is 25 requests a minute instead of 60, "
                            + "and every cover image comes back empty.");
        }
    }

    /** Free-text search, the way the add flow's box works. */
    public List<DiscogsResponses.SearchResult> search(String query, int limit) {
        return get(uri -> uri.path("/database/search")
                .queryParam("q", query)
                .queryParam("type", "release")
                .queryParam("per_page", limit)
                .build());
    }

    /**
     * Every pressing of one record, found by artist and title rather than by master id.
     *
     * <p>This is what bridges a MusicBrainz album to Discogs' pressings of it: the two
     * databases share no identifiers, but they agree on what a record is called. Verified
     * precise in practice — "Fred again.." plus "Ten Days" returns those four vinyl
     * pressings and nothing else.
     */
    public List<DiscogsResponses.SearchResult> pressingsOf(String artist, String title, int limit) {
        return get(uri -> uri.path("/database/search")
                .queryParam("artist", artist)
                .queryParam("release_title", title)
                .queryParam("type", "release")
                .queryParam("per_page", limit)
                .build());
    }

    /** A barcode identifies one pressing, which is exactly what a scan wants. */
    public List<DiscogsResponses.SearchResult> findByBarcode(String barcode) {
        return get(uri -> uri.path("/database/search")
                .queryParam("barcode", barcode)
                .queryParam("type", "release")
                .queryParam("per_page", 25)
                .build());
    }

    private List<DiscogsResponses.SearchResult> get(
            java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uri) {
        pacer.awaitSlot();
        try {
            DiscogsResponses.SearchResponse response = restClient
                    .get()
                    .uri(uri::apply)
                    .retrieve()
                    .body(DiscogsResponses.SearchResponse.class);
            if (response == null || response.results() == null) {
                return List.of();
            }
            return response.results();
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException("Discogs", e);
        }
    }

    /** Whether covers will come back at all, so callers can fall back rather than show gaps. */
    public boolean servesImages() {
        return properties.authenticated();
    }
}
