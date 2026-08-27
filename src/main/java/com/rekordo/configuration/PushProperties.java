package com.rekordo.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param endpoint Expo's push service, which fans out to APNs and FCM on our behalf. Nothing
 *                 here ever holds an Apple key — EAS does, and a device's Expo token is what
 *                 names it.
 * @param enabled  off by default, which is the local default for the same reason mail is:
 *                 nothing should be able to buzz a real phone from a laptop by accident.
 */
@ConfigurationProperties(prefix = "rekordo.push")
public record PushProperties(String endpoint, boolean enabled) {}
