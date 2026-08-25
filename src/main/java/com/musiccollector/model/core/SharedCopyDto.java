package com.musiccollector.model.core;

/**
 * A copy as somebody else sees it, which is deliberately less than the owner sees.
 *
 * <p>Notes never appear here at all — they are the one field written for nobody else — and
 * the grades and the money are filled in only when the viewer has earned them. Fields the
 * viewer may not see are null rather than absent, so one shape serves the friend view and
 * the public page and no client has to branch on which it asked for.
 */
public record SharedCopyDto(
        String id,
        String releaseId,
        /** Already resolved: the copy's own title when it was typed in by hand. */
        String title,
        String artistName,
        Integer year,
        /** Already resolved: the copy's override wins over the catalogue's answer. */
        Format format,
        String coverArtUrl,
        CoverThemeDto coverTheme,
        /** Media grade. Friends and up; null on a public page. */
        String condition,
        /** Sleeve grade. Friends and up; null on a public page. */
        String sleeveCondition,
        /** Null unless the owner has turned prices on. */
        Integer pricePaidCents,
        String currency,
        Long createdAt) {}
