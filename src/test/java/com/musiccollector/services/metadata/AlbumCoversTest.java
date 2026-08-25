package com.musiccollector.services.metadata;

import com.musiccollector.client.CoverArtClient;
import com.musiccollector.client.DiscogsClient;
import com.musiccollector.client.MusicBrainzClient;
import com.musiccollector.entity.ReleaseEntity;
import com.musiccollector.entity.ReleaseGroupEntity;
import com.musiccollector.model.core.AlbumCoverDto;
import com.musiccollector.model.core.Format;
import com.musiccollector.repository.ArtistImageRepository;
import com.musiccollector.repository.ReleaseGroupRepository;
import com.musiccollector.repository.ReleaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The album artwork a wishlist row draws.
 *
 * <p>The rule under test is "resolve, never fetch": every answer comes out of the mirror,
 * because a list of albums is a screen and not a reason to call two catalogues.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlbumCoversTest {

    private static final String MB_ALBUM = "musicbrainz:0f2d5a1e-4a1e-4e7a-9c1e-2f0d4b6a8c11";
    private static final String DISCOGS_ALBUM = "discogs:1283634";

    @Mock private MusicBrainzClient musicBrainzClient;
    @Mock private DiscogsClient discogsClient;
    @Mock private CoverArtClient coverArtClient;
    @Mock private ReleaseRepository releaseRepository;
    @Mock private ReleaseGroupRepository releaseGroupRepository;
    @Mock private ArtistImageRepository artistImageRepository;

    @InjectMocks private MetadataService service;

    private static ReleaseGroupEntity group(String externalId) {
        ReleaseGroupEntity entity = new ReleaseGroupEntity();
        entity.setId(UUID.randomUUID());
        entity.setExternalId(externalId);
        entity.setTitle("One More Light Live");
        entity.setArtistName("Linkin Park");
        entity.setFetchedAt(Instant.now());
        return entity;
    }

    private static ReleaseEntity release(
            ReleaseGroupEntity album, String externalId, String coverArtUrl, Boolean hasCoverArt) {
        ReleaseEntity entity = new ReleaseEntity();
        entity.setId(UUID.randomUUID());
        entity.setExternalId(externalId);
        entity.setReleaseGroupId(album.getId());
        entity.setTitle("One More Light Live");
        entity.setArtistName("Linkin Park");
        entity.setFormat(Format.VINYL);
        entity.setCoverArtUrl(coverArtUrl);
        entity.setHasCoverArt(hasCoverArt);
        entity.setFetchedAt(Instant.now());
        return entity;
    }

    private void mirror(Collection<ReleaseGroupEntity> groups, List<ReleaseEntity> releases) {
        when(releaseGroupRepository.findAllByExternalIdIn(any())).thenReturn(List.copyOf(groups));
        when(releaseRepository.findAllByReleaseGroupIdIn(any())).thenReturn(releases);
    }

    @Test
    void takesTheCoverOfAMirroredPressing() {
        ReleaseGroupEntity album = group(DISCOGS_ALBUM);
        mirror(List.of(album), List.of(release(album, "discogs:9", "https://img.discogs/9.jpg", true)));

        List<AlbumCoverDto> covers = service.albumCovers(List.of(DISCOGS_ALBUM));

        assertThat(covers).containsExactly(new AlbumCoverDto(DISCOGS_ALBUM, "https://img.discogs/9.jpg"));
        verify(discogsClient, never()).search(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void prefersAPressingKnownToHaveArtOverAnUnprobedOne() {
        ReleaseGroupEntity album = group(DISCOGS_ALBUM);
        mirror(
                List.of(album),
                List.of(
                        release(album, "discogs:1", "https://img.discogs/1.jpg", null),
                        release(album, "discogs:2", "https://img.discogs/2.jpg", true)));

        assertThat(service.albumCovers(List.of(DISCOGS_ALBUM)))
                .containsExactly(new AlbumCoverDto(DISCOGS_ALBUM, "https://img.discogs/2.jpg"));
    }

    @Test
    void ignoresAPressingKnownToHaveNoArt() {
        ReleaseGroupEntity album = group(DISCOGS_ALBUM);
        mirror(List.of(album), List.of(release(album, "discogs:1", "https://img.discogs/1.jpg", false)));

        assertThat(service.albumCovers(List.of(DISCOGS_ALBUM)))
                .containsExactly(new AlbumCoverDto(DISCOGS_ALBUM, null));
    }

    @Test
    void fallsBackToTheArchiveForAnUnmirroredMusicBrainzAlbum() {
        mirror(List.of(), List.of());
        when(coverArtClient.frontCoverUrlForGroup(any()))
                .thenAnswer(call -> "https://coverartarchive.org/release-group/" + call.getArgument(0) + "/front-500");

        assertThat(service.albumCovers(List.of(MB_ALBUM)))
                .containsExactly(new AlbumCoverDto(
                        MB_ALBUM,
                        "https://coverartarchive.org/release-group/0f2d5a1e-4a1e-4e7a-9c1e-2f0d4b6a8c11/front-500"));
    }

    @Test
    void leavesOutHandEnteredAlbumsAndAsksForNothingTwice() {
        ReleaseGroupEntity album = group(DISCOGS_ALBUM);
        mirror(List.of(album), List.of(release(album, "discogs:9", "https://img.discogs/9.jpg", true)));

        List<AlbumCoverDto> covers = service.albumCovers(
                List.of("local:" + UUID.randomUUID(), DISCOGS_ALBUM, DISCOGS_ALBUM));

        assertThat(covers).containsExactly(new AlbumCoverDto(DISCOGS_ALBUM, "https://img.discogs/9.jpg"));
    }

    @Test
    void answersNullForAnUnmirroredDiscogsAlbum() {
        mirror(List.of(), List.of());

        assertThat(service.albumCovers(List.of(DISCOGS_ALBUM)))
                .containsExactly(new AlbumCoverDto(DISCOGS_ALBUM, null));
        verify(coverArtClient, never()).frontCoverUrlForGroup(any());
    }
}
