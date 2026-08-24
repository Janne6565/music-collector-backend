package com.musiccollector.client;

import com.musiccollector.configuration.MusicBrainzProperties;
import com.musiccollector.model.exception.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

/**
 * Talks to the MusicBrainz web service.
 *
 * <p>Every outbound call goes through {@link UpstreamPacer}, because MusicBrainz bans
 * clients that exceed one request per second. That pacing is per process — the app runs a
 * single replica, and scaling out would need a shared limiter instead.
 */
@Component
public class MusicBrainzClient {

    private static final Logger log = LoggerFactory.getLogger(MusicBrainzClient.class);
    private static final String LOOKUP_INCLUDES = "artist-credits+labels+release-groups+media";

    private final RestClient restClient;
    private final UpstreamPacer pacer;

    public MusicBrainzClient(RestClient musicBrainzRestClient, MusicBrainzProperties properties) {
        this.restClient = musicBrainzRestClient;
        this.pacer = new UpstreamPacer(properties.requestsPerSecond());
    }

    public List<MusicBrainzResponses.Release> searchReleases(String query, int limit) {
        return search("release", query, limit);
    }

    public List<MusicBrainzResponses.Release> findByBarcode(String barcode) {
        // Quoted so a barcode is matched as one term rather than tokenised by Lucene.
        return search("release", "barcode:\"" + barcode + "\"", 25);
    }

    private List<MusicBrainzResponses.Release> search(String resource, String query, int limit) {
        pacer.awaitSlot();
        try {
            MusicBrainzResponses.SearchResponse response = restClient
                    .get()
                    .uri(uri -> uri.path("/" + resource)
                            .queryParam("query", query)
                            .queryParam("limit", limit)
                            .queryParam("fmt", "json")
                            .build())
                    .retrieve()
                    .body(MusicBrainzResponses.SearchResponse.class);
            if (response == null || response.releases() == null) {
                return List.of();
            }
            log.debug("MusicBrainz search '{}' returned {} releases", query, response.releases().size());
            return response.releases();
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException("MusicBrainz", e);
        }
    }

    public Optional<MusicBrainzResponses.Release> lookupRelease(String mbid) {
        pacer.awaitSlot();
        try {
            return Optional.ofNullable(restClient
                    .get()
                    .uri(uri -> uri.path("/release/{mbid}")
                            .queryParam("inc", LOOKUP_INCLUDES)
                            .queryParam("fmt", "json")
                            .build(mbid))
                    .retrieve()
                    .body(MusicBrainzResponses.Release.class));
        } catch (HttpClientErrorException.NotFound e) {
            // MusicBrainz genuinely has no such release. That is a 404 for our caller, not
            // an upstream failure — reporting it as 502 would tell the client to retry.
            return Optional.empty();
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException("MusicBrainz", e);
        }
    }
}
