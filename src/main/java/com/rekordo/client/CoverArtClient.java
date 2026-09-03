package com.rekordo.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fetches front-cover thumbnails from the Cover Art Archive.
 *
 * <p>A missing cover is normal — plenty of releases have none — so an absent cover is
 * reported rather than raised, and the release still imports, just without a theme. An
 * archive that did not answer is reported as a third thing again: see {@link CoverProbe}.
 */
@Component
public class CoverArtClient {

    private static final Logger log = LoggerFactory.getLogger(CoverArtClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public CoverArtClient(RestClient coverArtRestClient,
                          com.rekordo.configuration.MusicBrainzProperties properties) {
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

    /**
     * The 250px thumbnail, used only for sampling the palette.
     *
     * <p>The archive says "nothing here" with a 404, and that is the only answer worth
     * remembering. A timeout, a 429 or a 5xx says nothing about this release at all — see
     * {@link CoverProbe}.
     */
    public CoverProbe fetchThumbnail(String releaseId) {
        try {
            byte[] bytes = restClient
                    .get()
                    .uri("/release/{mbid}/front-250", releaseId)
                    .retrieve()
                    .body(byte[].class);
            return bytes == null ? CoverProbe.absent() : CoverProbe.found(bytes);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Gone e) {
            log.debug("No cover art for release {}", releaseId);
            return CoverProbe.absent();
        } catch (RestClientException e) {
            log.debug("Cover art archive did not answer for release {} ({})", releaseId, e.getMessage());
            return CoverProbe.unreachable();
        }
    }
}
