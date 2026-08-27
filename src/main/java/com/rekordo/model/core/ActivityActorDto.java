package com.rekordo.model.core;

import java.util.UUID;

/**
 * Who did the thing. Deliberately thinner than {@link ProfileSummaryDto}: a feed of fifty
 * lines would otherwise count fifty collections to draw fifty names.
 */
public record ActivityActorDto(UUID id, String handle, String displayName) {}
