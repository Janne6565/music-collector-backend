package com.musiccollector.services.notifications;

import java.util.Map;

/**
 * One push, already resolved to the tokens it goes to.
 *
 * @param title cut by iOS at roughly one line; write it to survive that
 * @param body  two lines, roughly 90 characters before the ellipsis (design 22c)
 * @param data  what the app opens when it is tapped. Never anything private: a payload rides
 *              through Apple's and Google's servers to get here.
 */
public record PushMessage(String token, String title, String body, Map<String, String> data) {}
