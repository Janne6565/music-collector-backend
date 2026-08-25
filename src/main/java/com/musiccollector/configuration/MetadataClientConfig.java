package com.musiccollector.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

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
    public RestClient musicBrainzRestClient(MusicBrainzProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("User-Agent", properties.userAgent())
                .requestFactory(timeouts())
                .build();
    }

    /**
     * Discogs rejects the default Java User-Agent outright, and serves images only to a
     * request that carries a token — so the token, when there is one, is a default header
     * rather than something every call site has to remember.
     */
    @Bean
    public RestClient discogsRestClient(DiscogsProperties properties) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("User-Agent", properties.userAgent())
                .requestFactory(timeouts());
        if (properties.authenticated()) {
            builder = builder.defaultHeader("Authorization", "Discogs token=" + properties.token());
        }
        return builder.build();
    }

    @Bean
    public RestClient coverArtRestClient(MusicBrainzProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.coverArtBaseUrl())
                .defaultHeader("User-Agent", properties.userAgent())
                .requestFactory(timeouts())
                .build();
    }
}
