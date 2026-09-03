package com.rekordo.services.metadata;

import com.rekordo.client.CoverArtClient;
import com.rekordo.client.CoverProbe;
import com.rekordo.client.DiscogsClient;
import com.rekordo.client.MusicBrainzClient;
import com.rekordo.entity.ReleaseEntity;
import com.rekordo.entity.ReleaseGroupEntity;
import com.rekordo.model.core.Format;
import com.rekordo.model.core.ReleaseDto;
import com.rekordo.repository.ArtistImageRepository;
import com.rekordo.repository.ReleaseGroupRepository;
import com.rekordo.repository.ReleaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a cover probe is allowed to conclude.
 *
 * <p>The palette sampler used to treat every empty answer alike, so an archive that timed
 * out or throttled was written down as an archive that has no picture. That is served to
 * clients as a null cover URL, and they cache it — which is how a record lost its sleeve
 * everywhere at once, for good, during an evening of scanning. Reported from the field.
 *
 * <p>So: a definite no is remembered, and an unanswered question is left open.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoverProbeTest {

    private static final String MBID = "0f2d5a1e-4a1e-4e7a-9c1e-2f0d4b6a8c11";
    private static final String MB_RELEASE = "musicbrainz:" + MBID;
    private static final String COVER_URL = "https://coverartarchive.org/release/" + MBID + "/front-500";

    @Mock private MusicBrainzClient musicBrainzClient;
    @Mock private DiscogsClient discogsClient;
    @Mock private CoverArtClient coverArtClient;
    @Mock private DominantColorExtractor colorExtractor;
    @Mock private ReleaseRepository releaseRepository;
    @Mock private ReleaseGroupRepository releaseGroupRepository;
    @Mock private ArtistImageRepository artistImageRepository;
    @Mock private TrackMirror trackMirror;

    @InjectMocks private MetadataService service;

    private ReleaseEntity mirrored() {
        ReleaseGroupEntity album = new ReleaseGroupEntity();
        album.setId(UUID.randomUUID());
        album.setExternalId("musicbrainz:1c4a2f6e-2b3d-4c5e-8a9b-0d1e2f3a4b5c");
        album.setTitle("Bitches Brew");
        album.setArtistName("Miles Davis");
        album.setFetchedAt(Instant.now());

        ReleaseEntity entity = new ReleaseEntity();
        entity.setId(UUID.randomUUID());
        entity.setExternalId(MB_RELEASE);
        entity.setReleaseGroupId(album.getId());
        entity.setTitle("Bitches Brew");
        entity.setArtistName("Miles Davis");
        entity.setFormat(Format.VINYL);
        entity.setCoverArtUrl(COVER_URL);
        // Not probed yet: exactly the state a release persisted from a search is in.
        entity.setHasCoverArt(null);
        entity.setFetchedAt(Instant.now());

        when(releaseRepository.findByExternalId(MB_RELEASE)).thenReturn(Optional.of(entity));
        when(releaseGroupRepository.findById(album.getId())).thenReturn(Optional.of(album));
        return entity;
    }

    @Test
    void keepsTheCoverWhenTheArchiveCouldNotBeReached() {
        ReleaseEntity entity = mirrored();
        when(coverArtClient.fetchThumbnail(MBID)).thenReturn(CoverProbe.unreachable());

        ReleaseDto release = service.getRelease(MB_RELEASE);

        assertThat(release.coverArtUrl()).isEqualTo(COVER_URL);
        // Nothing learned, so nothing written: the next lookup asks again.
        assertThat(entity.getHasCoverArt()).isNull();
        verify(releaseRepository, never()).save(any());
    }

    @Test
    void remembersADefiniteNo() {
        ReleaseEntity entity = mirrored();
        when(coverArtClient.fetchThumbnail(MBID)).thenReturn(CoverProbe.absent());

        ReleaseDto release = service.getRelease(MB_RELEASE);

        assertThat(release.coverArtUrl()).isNull();
        assertThat(entity.getHasCoverArt()).isFalse();
        verify(releaseRepository).save(entity);
    }

    @Test
    void samplesThePaletteWhenTheCoverCameBack() {
        ReleaseEntity entity = mirrored();
        byte[] bytes = {1, 2, 3};
        when(coverArtClient.fetchThumbnail(MBID)).thenReturn(CoverProbe.found(bytes));
        when(colorExtractor.extract(bytes))
                .thenReturn(Optional.of(new CoverPalette("#101010", "#a2573a", 0.2)));

        ReleaseDto release = service.getRelease(MB_RELEASE);

        assertThat(release.coverArtUrl()).isEqualTo(COVER_URL);
        assertThat(entity.getHasCoverArt()).isTrue();
        assertThat(entity.getDominantColor()).isEqualTo("#101010");
    }
}
