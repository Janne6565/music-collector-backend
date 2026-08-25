package com.musiccollector.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Fetches front-cover thumbnails from the Cover Art Archive.
 *
 * <p>A missing cover is normal — plenty of releases have none — so a failure here is
 * returned as empty rather than raised. The release still imports, just without a theme.
 */
@Component
public class CoverArtClient {

    private static final Logger log = LoggerFactory.getLogger(CoverArtClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public CoverArtClient(RestClient coverArtRestClient,
                          com.musiccollector.configuration.MusicBrainzProperties properties) {
        this.restClient = coverArtRestClient;
        this.baseUrl = properties.coverArtBaseUrl();
    }

    /** The public URL clients should render, whether or not the bytes were fetchable. */
    public String frontCoverUrl(String releaseId) {
        return baseUrl + "/release/" + releaseId + "/front-500";
    }

    /**
     * The album's cover, rather than one pressing's.
     *
     * A discography row is an album, and picking one of its 47 pressings just to have
     * something to show would be arbitrary. The archive resolves this per release group.
     */
    public String frontCoverUrlForGroup(String releaseGroupMbid) {
        return baseUrl + "/release-group/" + releaseGroupMbid + "/front-500";
    }

    /** The 250px thumbnail, used only for sampling the palette. */
    public Optional<byte[]> fetchThumbnail(String releaseId) {
        try {
            return Optional.ofNullable(restClient
                    .get()
                    .uri("/release/{mbid}/front-250", releaseId)
                    .retrieve()
                    .body(byte[].class));
        } catch (RestClientException e) {
            log.debug("No cover art for release {} ({})", releaseId, e.getMessage());
            return Optional.empty();
        }
    }
}
