package com.rekordo.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The three clients that talk to somebody else's API.
 *
 * <p>Each is built from the injected {@link RestClient.Builder} rather than
 * {@code RestClient.builder()}. The injected one is Boot's, already wired to the
 * observation registry, and that is what turns every outbound call into an
 * {@code http.client.requests} timer and a client span. Built from scratch, these three
 * were the least observable part of the service and the most likely to be the reason it
 * was slow: MusicBrainz is rate-limited to one request a second and Discogs to twenty-five
 * a minute, so they are exactly where waiting happens.
 */
@Configuration
@EnableConfigurationProperties({
    MusicBrainzProperties.class,
    DiscogsProperties.class,
    JwtProperties.class,
    OAuthProperties.class
})
public class MetadataClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private static ClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Bean
    public RestClient musicBrainzRestClient(RestClient.Builder builder, MusicBrainzProperties properties) {
        return observed(builder)
                .baseUrl(properties.baseUrl())
                .defaultHeader("User-Agent", properties.userAgent())
                .build();
    }

    /**
     * A copy of Boot's builder with this project's timeouts.
     *
     * <p>Cloned because the injected builder is shared: configuring it in place would give
     * the last bean defined here the base URL of them all.
     *
     * <p>Nothing here names the API. Spring's client observation already tags every timer
     * with {@code client.name}, taken from the request's host, which is what tells
     * musicbrainz.org, api.discogs.com and coverartarchive.org apart on a dashboard.
     */
    private static RestClient.Builder observed(RestClient.Builder builder) {
        return builder.clone().requestFactory(timeouts());
    }

    /**
     * Discogs rejects the default Java User-Agent outright, and serves images only to a
     * request that carries a token — so the token, when there is one, is a default header
     * rather than something every call site has to remember.
     */
    @Bean
    public RestClient discogsRestClient(RestClient.Builder base, DiscogsProperties properties) {
        RestClient.Builder builder = observed(base)
                .baseUrl(properties.baseUrl())
                .defaultHeader("User-Agent", properties.userAgent());
        if (properties.authenticated()) {
            builder = builder.defaultHeader("Authorization", "Discogs token=" + properties.token());
        }
        return builder.build();
    }

    @Bean
    public RestClient coverArtRestClient(RestClient.Builder builder, MusicBrainzProperties properties) {
        return observed(builder)
                .baseUrl(properties.coverArtBaseUrl())
                .defaultHeader("User-Agent", properties.userAgent())
                .build();
    }
}
