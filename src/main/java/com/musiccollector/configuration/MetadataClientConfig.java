package com.musiccollector.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({MusicBrainzProperties.class, JwtProperties.class})
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

    @Bean
    public RestClient coverArtRestClient(MusicBrainzProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.coverArtBaseUrl())
                .defaultHeader("User-Agent", properties.userAgent())
                .requestFactory(timeouts())
                .build();
    }
}
