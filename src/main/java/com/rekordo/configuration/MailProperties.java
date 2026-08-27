package com.rekordo.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiKey   scoped Mail Service key. Blank disables sending entirely, which is the
 *                 local-development default — nothing should be able to mail a real person
 *                 from a laptop by accident.
 * @param from     the public base URL links in mail should point at
 */
@ConfigurationProperties(prefix = "rekordo.mail")
public record MailProperties(String baseUrl, String apiKey, String publicUrl) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
