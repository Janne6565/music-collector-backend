package com.rekordo.model.core;

import java.util.List;

/**
 * Someone else's shelf.
 *
 * @param truncated whether the cap cut the list short. Said out loud rather than silently
 *                  returning fewer, because the client derives the format counts under the
 *                  grid from what it was given and would otherwise report them as fact.
 */
public record SharedCollectionDto(List<SharedCopyDto> copies, long total, boolean truncated) {}
