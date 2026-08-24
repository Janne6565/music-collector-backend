package com.musiccollector.services.metadata;

import com.musiccollector.client.CoverArtClient;
import com.musiccollector.client.MusicBrainzClient;
import com.musiccollector.client.MusicBrainzResponses;
import com.musiccollector.configuration.CacheConfig;
import com.musiccollector.entity.ReleaseEntity;
import com.musiccollector.entity.ReleaseGroupEntity;
import com.musiccollector.model.core.ReleaseDto;
import com.musiccollector.model.exception.ReleaseNotFoundException;
import com.musiccollector.repository.ReleaseGroupRepository;
import com.musiccollector.repository.ReleaseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The read side of the catalog: search, barcode lookup and single-release detail, backed by
 * a local mirror of MusicBrainz.
 *
 * <p>Two deliberate choices about when work happens:
 *
 * <ul>
 *   <li><b>Search does not sample cover art.</b> Twenty-five results would mean twenty-five
 *       image fetches and decodes to build themes nobody has asked for yet. The palette is
 *       computed on the detail lookup — the moment the user actually picks a release.
 *   <li><b>Barcode scans check the mirror first.</b> A scanned EAN that someone has looked
 *       up before never reaches MusicBrainz, which is what makes the open, unauthenticated
 *       proxy survivable under a one-request-per-second upstream cap.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);

    private final MusicBrainzClient musicBrainzClient;
    private final CoverArtClient coverArtClient;
    private final DominantColorExtractor colorExtractor;
    private final ReleaseRepository releaseRepository;
    private final ReleaseGroupRepository releaseGroupRepository;

    @Cacheable(cacheNames = CacheConfig.METADATA_SEARCH, key = "#query + '|' + #limit")
    @Transactional
    public List<ReleaseDto> search(String query, int limit) {
        return musicBrainzClient.searchReleases(query, limit).stream()
                .map(this::upsert)
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional
    public List<ReleaseDto> findByBarcode(String barcode) {
        List<ReleaseEntity> known = releaseRepository.findAllByBarcode(barcode);
        if (!known.isEmpty()) {
            log.debug("Barcode {} served from the local mirror ({} releases)", barcode, known.size());
            return known.stream().map(this::toDto).toList();
        }
        return musicBrainzClient.findByBarcode(barcode).stream()
                .map(this::upsert)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Full detail for one release, including its cover theme. The palette is sampled and
     * persisted on the first lookup and reused by every caller afterwards.
     */
    @Transactional
    public ReleaseDto getRelease(UUID mbid) {
        ReleaseEntity entity = releaseRepository
                .findByMbid(mbid)
                .or(() -> musicBrainzClient
                        .lookupRelease(mbid.toString())
                        .flatMap(this::persist))
                .orElseThrow(() -> new ReleaseNotFoundException(mbid));

        if (entity.getDominantColor() == null) {
            applyCoverPalette(entity);
        }
        return toDto(entity);
    }

    private Optional<ReleaseDto> upsert(MusicBrainzResponses.Release release) {
        return releaseRepository
                .findByMbid(UUID.fromString(release.id()))
                .or(() -> persist(release))
                .map(this::toDto);
    }

    private Optional<ReleaseEntity> persist(MusicBrainzResponses.Release release) {
        if (release.id() == null || release.releaseGroup() == null || release.releaseGroup().id() == null) {
            // Without a release group there is no album to hang other copies off, which the
            // detail screen's "other copies of this release" block depends on.
            log.debug("Skipping release {} — no release group in the payload", release.id());
            return Optional.empty();
        }
        ReleaseGroupEntity group = ensureReleaseGroup(release);

        ReleaseEntity entity = new ReleaseEntity();
        entity.setId(UUID.randomUUID());
        entity.setMbid(UUID.fromString(release.id()));
        entity.setReleaseGroupId(group.getId());
        entity.setTitle(release.title() == null ? group.getTitle() : release.title());
        entity.setArtistName(MetadataMapper.artistName(release));
        entity.setFormat(MetadataMapper.format(release));
        entity.setYear(MetadataMapper.year(release.date()));
        entity.setLabel(MetadataMapper.label(release));
        entity.setCatalogNumber(MetadataMapper.catalogNumber(release));
        entity.setCountry(release.country());
        entity.setBarcode(release.barcode());
        entity.setCoverArtUrl(coverArtClient.frontCoverUrl(release.id()));
        entity.setFetchedAt(Instant.now());
        return Optional.of(releaseRepository.save(entity));
    }

    private ReleaseGroupEntity ensureReleaseGroup(MusicBrainzResponses.Release release) {
        UUID groupMbid = UUID.fromString(release.releaseGroup().id());
        return releaseGroupRepository.findByMbid(groupMbid).orElseGet(() -> {
            ReleaseGroupEntity group = new ReleaseGroupEntity();
            group.setId(UUID.randomUUID());
            group.setMbid(groupMbid);
            group.setTitle(Optional.ofNullable(release.releaseGroup().title())
                    .orElseGet(() -> Optional.ofNullable(release.title()).orElse("Untitled")));
            group.setArtistName(MetadataMapper.artistName(release));
            group.setArtistMbid(MetadataMapper.artistMbid(release));
            group.setFirstReleaseYear(MetadataMapper.year(release.releaseGroup().firstReleaseDate()));
            group.setFetchedAt(Instant.now());
            return releaseGroupRepository.save(group);
        });
    }

    private void applyCoverPalette(ReleaseEntity entity) {
        coverArtClient
                .fetchThumbnail(entity.getMbid().toString())
                .flatMap(colorExtractor::extract)
                .ifPresent(palette -> {
                    entity.setDominantColor(palette.dominantColor());
                    entity.setAccentColor(palette.accentColor());
                    entity.setLuminance(palette.luminance());
                    releaseRepository.save(entity);
                    log.debug("Sampled cover for {}: {} (luminance {})",
                            entity.getMbid(), palette.dominantColor(), palette.luminance());
                });
    }

    private ReleaseDto toDto(ReleaseEntity entity) {
        UUID groupMbid = releaseGroupRepository
                .findById(entity.getReleaseGroupId())
                .map(ReleaseGroupEntity::getMbid)
                .orElse(null);
        return MetadataMapper.toDto(entity, groupMbid);
    }
}
