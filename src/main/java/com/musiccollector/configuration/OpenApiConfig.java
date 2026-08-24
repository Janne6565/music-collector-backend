package com.musiccollector.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Music Collector API")
                .version("v1")
                .description("""
                        Auth, per-entity sync and the MusicBrainz metadata proxy.

                        The collection itself is not served from here: clients are local-first
                        and read their library from a local store, so there are no CRUD endpoints
                        for copies. The server participates only as a sync peer."""));
    }
}
