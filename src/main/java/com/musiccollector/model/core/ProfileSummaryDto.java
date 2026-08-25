package com.musiccollector.model.core;

import java.util.UUID;

/**
 * One person as they appear in a list — a search result, a row of the People panel, the
 * face on a request card.
 *
 * <p>{@code copyCount} is null when the viewer may not see the collection: the count is
 * itself information about a shelf that has been closed, and "312 copies" under a locked
 * profile tells a stranger more than the owner agreed to. The private profile screen names
 * a total deliberately and gets it from the fuller {@link ProfileDto}.
 */
public record ProfileSummaryDto(
        UUID id,
        String handle,
        String displayName,
        Long copyCount,
        RelationshipDto relationship,
        boolean collectionPrivate) {}
