package com.musiccollector.configuration;

import com.musiccollector.services.mail.MailPort;
import com.musiccollector.services.mail.MailServiceClient;
import com.musiccollector.services.mail.NoopMailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    public MailPort mailPort(MailProperties properties) {
        if (!properties.enabled()) {
            log.info("No mail API key configured — password reset links will be logged, not sent");
            return new NoopMailPort();
        }
        return new MailServiceClient(RestClient.builder().build(), properties.baseUrl(), properties.apiKey());
    }
}
