package com.musiccollector.services.metadata;

import com.musiccollector.client.CoverArtClient;
import com.musiccollector.client.DiscogsClient;
import com.musiccollector.client.MusicBrainzClient;
import com.musiccollector.entity.ReleaseEntity;
import com.musiccollector.entity.ReleaseGroupEntity;
import com.musiccollector.model.core.Format;
import com.musiccollector.model.core.ReleaseDto;
import com.musiccollector.repository.ArtistImageRepository;
import com.musiccollector.repository.ReleaseGroupRepository;
import com.musiccollector.repository.ReleaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * What a freshly signed-in device asks for: the catalogue behind copies it has just pulled.
 *
 * <p>The rule under test is the same one the wishlist covers follow — resolve from the
 * mirror, never call a catalogue. A collection of two hundred records arriving on a second
 * device must not become two hundred paced upstream lookups.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetReleasesTest {

    private static final String MB_RELEASE = "musicbrainz:0f2d5a1e-4a1e-4e7a-9c1e-2f0d4b6a8c11";
    private static final String DISCOGS_RELEASE = "discogs:31679120";

    @Mock private MusicBrainzClient musicBrainzClient;
    @Mock private DiscogsClient discogsClient;
    @Mock private CoverArtClient coverArtClient;
    @Mock private ReleaseRepository releaseRepository;
    @Mock private ReleaseGroupRepository releaseGroupRepository;
    @Mock private ArtistImageRepository artistImageRepository;

    @InjectMocks private MetadataService service;

    private static final ReleaseGroupEntity ALBUM = album();

    private static ReleaseGroupEntity album() {
        ReleaseGroupEntity entity = new ReleaseGroupEntity();
        entity.setId(UUID.randomUUID());
        entity.setExternalId("musicbrainz:1c4a2f6e-2b3d-4c5e-8a9b-0d1e2f3a4b5c");
        entity.setTitle("Bitches Brew");
        entity.setArtistName("Miles Davis");
        entity.setFetchedAt(Instant.now());
        return entity;
    }

    private static ReleaseEntity release(String externalId) {
        ReleaseEntity entity = new ReleaseEntity();
        entity.setId(UUID.randomUUID());
        entity.setExternalId(externalId);
        entity.setReleaseGroupId(ALBUM.getId());
        entity.setTitle("Bitches Brew");
        entity.setArtistName("Miles Davis");
        entity.setFormat(Format.VINYL);
        entity.setFetchedAt(Instant.now());
        return entity;
    }

    private void mirror(List<ReleaseEntity> releases) {
        when(releaseRepository.findAllByExternalIdIn(any())).thenReturn(releases);
        when(releaseGroupRepository.findAllById(any())).thenReturn(List.of(ALBUM));
    }

    @Test
    void answersFromTheMirrorForBothCatalogues() {
        mirror(List.of(release(MB_RELEASE), release(DISCOGS_RELEASE)));

        List<ReleaseDto> releases = service.getReleases(List.of(MB_RELEASE, DISCOGS_RELEASE));

        assertThat(releases).extracting(ReleaseDto::id).containsExactlyInAnyOrder(MB_RELEASE, DISCOGS_RELEASE);
        assertThat(releases).allSatisfy(release -> assertThat(release.albumId()).isEqualTo(ALBUM.getExternalId()));
    }

    @Test
    void neverCallsACatalogueForAReleaseTheMirrorDoesNotHold() {
        mirror(List.of());

        assertThat(service.getReleases(List.of(MB_RELEASE))).isEmpty();
        verify(musicBrainzClient, never()).lookupRelease(any());
        verify(discogsClient, never()).pressingsOf(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void asksOnceForARepeatedId() {
        mirror(List.of(release(MB_RELEASE)));

        service.getReleases(List.of(MB_RELEASE, MB_RELEASE));

        ArgumentCaptor<Collection<String>> asked = ArgumentCaptor.captor();
        verify(releaseRepository).findAllByExternalIdIn(asked.capture());
        assertThat(asked.getValue()).containsExactly(MB_RELEASE);
    }

    @Test
    void leavesHandEnteredReleasesOut() {
        assertThat(service.getReleases(List.of("local:8f1c9d2e-0000-4000-8000-000000000001"))).isEmpty();
        verify(releaseRepository, never()).findAllByExternalIdIn(any());
    }

    @Test
    void treatsAnUnprefixedIdAsMusicBrainz() {
        String bare = "0f2d5a1e-4a1e-4e7a-9c1e-2f0d4b6a8c11";
        mirror(List.of(release(MB_RELEASE)));

        service.getReleases(List.of(bare));

        ArgumentCaptor<Collection<String>> asked = ArgumentCaptor.captor();
        verify(releaseRepository).findAllByExternalIdIn(asked.capture());
        assertThat(asked.getValue()).containsExactly(MB_RELEASE);
    }
}
