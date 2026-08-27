package com.rekordo.model.core;

import java.util.List;

/**
 * A page of an artist's discography, plus how many the query matched upstream.
 *
 * <p>The two numbers differ on purpose. `albums` is what fits in one request; `total` is
 * what MusicBrainz says exists, which is what the type chips on screens 10c and 10d show.
 * Counting the page instead would tell someone Daughter has 25 singles when she has 22 and
 * Miles Davis has 25 albums when he has 51.
 */
public record DiscographyDto(List<AlbumDto> albums, int total) {}
