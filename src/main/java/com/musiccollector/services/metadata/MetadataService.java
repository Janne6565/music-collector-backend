package com.musiccollector.services.metadata;

import com.musiccollector.client.CoverArtClient;
import com.musiccollector.client.MusicBrainzClient;
import com.musiccollector.client.MusicBrainzResponses;
import com.musiccollector.configuration.CacheConfig;
import com.musiccollector.entity.ReleaseEntity;
import com.musiccollector.entity.ReleaseGroupEntity;
import com.musiccollector.model.core.AlbumDto;
import com.musiccollector.model.core.ArtistDto;
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
     * Artists matching a name, most confident first.
     *
     * Kept separate from {@link #search} rather than merged into it: they are two upstream
     * requests a second apart, and a client that renders artists the moment they land reads
     * as faster than one that waits to show both at once.
     */
    @Transactional(readOnly = true)
    public List<ArtistDto> searchArtists(String query, int limit) {
        return musicBrainzClient.searchArtists(query, limit).stream()
                .map(MetadataMapper::toArtistDto)
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(
                        ArtistDto::score, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
    }

    /**
     * One artist's discography, narrowed to a primary type.
     *
     * The total is the count MusicBrainz reports for the query, not the size of the page —
     * a client showing "Albums 51" is telling the truth even though it only received 25.
     */
    @Transactional(readOnly = true)
    public Discography albumsOfArtist(UUID artistMbid, String primaryType, int limit) {
        String query = primaryType == null || primaryType.isBlank()
                ? "arid:" + artistMbid
                // Quoted so a two-word type cannot be split into two terms by Lucene.
                : "arid:" + artistMbid + " AND primarytype:\"" + primaryType + "\"";
        MusicBrainzResponses.ReleaseGroupSearchResponse response =
                musicBrainzClient.searchReleaseGroups(query, limit);
        List<AlbumDto> albums = (response.releaseGroups() == null ? List.<MusicBrainzResponses.ReleaseGroup>of()
                        : response.releaseGroups())
                .stream()
                .map(group -> MetadataMapper.toAlbumDto(group, coverArtClient.frontCoverUrlForGroup(group.id())))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new Discography(albums, response.count());
    }

    /** A page of a discography plus how many the query matched in total. */
    public record Discography(List<AlbumDto> albums, int total) {}

    /**
     * Every pressing of one album, newest metadata first.
     *
     * Persisted on the way through like any other release, so adding one of them straight
     * from the pressing table works offline afterwards.
     */
    @Transactional
    public List<ReleaseDto> releasesInGroup(UUID releaseGroupMbid, int limit) {
        return musicBrainzClient.findReleasesInGroup(releaseGroupMbid.toString(), limit).stream()
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

        // Skipped once we know there is no cover: without this the palette fetch runs on
        // every single lookup of an artless release, and always comes back empty.
        if (entity.getDominantColor() == null && !Boolean.FALSE.equals(entity.getHasCoverArt())) {
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
        entity.setReleaseDate(release.date());
        entity.setTrackCount(release.trackCount());
        entity.setDiscCount(MetadataMapper.discCount(release));
        entity.setCoverArtUrl(coverArtClient.frontCoverUrl(release.id()));
        // A lookup tells us whether there is a front cover; a search does not mention it at
        // all. Null is therefore "not asked yet", not "no cover" — see toDto.
        entity.setHasCoverArt(
                release.coverArtArchive() == null ? null : release.coverArtArchive().front());
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

    /**
     * Samples the cover for its palette, and records whether there was a cover at all.
     *
     * The fetch answers both questions at once, so a release that reaches here stops being
     * "unknown" either way — that is what lets the search-persisted rows, which MusicBrainz
     * never told us about, eventually get a truthful answer.
     */
    private void applyCoverPalette(ReleaseEntity entity) {
        Optional<byte[]> thumbnail = coverArtClient.fetchThumbnail(entity.getMbid().toString());
        entity.setHasCoverArt(thumbnail.isPresent());

        thumbnail.flatMap(colorExtractor::extract).ifPresentOrElse(palette -> {
            entity.setDominantColor(palette.dominantColor());
            entity.setAccentColor(palette.accentColor());
            entity.setLightness(palette.lightness());
            log.debug("Sampled cover for {}: {} (lightness {})",
                    entity.getMbid(), palette.dominantColor(), palette.lightness());
        }, () -> log.debug("No cover art for release {}", entity.getMbid()));

        releaseRepository.save(entity);
    }

    private ReleaseDto toDto(ReleaseEntity entity) {
        UUID groupMbid = releaseGroupRepository
                .findById(entity.getReleaseGroupId())
                .map(ReleaseGroupEntity::getMbid)
                .orElse(null);
        return MetadataMapper.toDto(entity, groupMbid);
    }
}
