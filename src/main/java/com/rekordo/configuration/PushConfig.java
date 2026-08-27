package com.rekordo.configuration;

import com.rekordo.services.notifications.ExpoPushClient;
import com.rekordo.services.notifications.NoopPushPort;
import com.rekordo.services.notifications.PushPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    public PushPort pushPort(PushProperties properties) {
        if (!properties.enabled()) {
            log.info("Push is not enabled — notifications will be logged, not sent");
            return new NoopPushPort();
        }
        return new ExpoPushClient(RestClient.builder().build(), properties.endpoint());
    }
}
