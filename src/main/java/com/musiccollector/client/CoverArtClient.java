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
    public String frontCoverUrl(String releaseMbid) {
        return baseUrl + "/release/" + releaseMbid + "/front-500";
    }

    /** The 250px thumbnail, used only for sampling the palette. */
    public Optional<byte[]> fetchThumbnail(String releaseMbid) {
        try {
            return Optional.ofNullable(restClient
                    .get()
                    .uri("/release/{mbid}/front-250", releaseMbid)
                    .retrieve()
                    .body(byte[].class));
        } catch (RestClientException e) {
            log.debug("No cover art for release {} ({})", releaseMbid, e.getMessage());
            return Optional.empty();
        }
    }
}
