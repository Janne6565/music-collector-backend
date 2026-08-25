package com.musiccollector.services.metadata;

import com.musiccollector.client.CoverArtClient;
import com.musiccollector.client.DiscogsClient;
import com.musiccollector.client.DiscogsResponses;
import com.musiccollector.client.MusicBrainzClient;
import com.musiccollector.client.MusicBrainzResponses;
import com.musiccollector.configuration.CacheConfig;
import com.musiccollector.entity.ArtistImageEntity;
import com.musiccollector.entity.ReleaseEntity;
import com.musiccollector.entity.ReleaseGroupEntity;
import com.musiccollector.model.core.AlbumDto;
import com.musiccollector.model.core.ArtistDto;
import com.musiccollector.model.core.ArtistImageDto;
import com.musiccollector.model.core.ExternalRef;
import com.musiccollector.model.core.ReleaseSource;
import com.musiccollector.model.core.ReleaseDto;
import com.musiccollector.model.exception.ReleaseNotFoundException;
import com.musiccollector.model.exception.UpstreamUnavailableException;
import com.musiccollector.repository.ArtistImageRepository;
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
    private final DiscogsClient discogsClient;
    private final CoverArtClient coverArtClient;
    private final DominantColorExtractor colorExtractor;
    private final ReleaseRepository releaseRepository;
    private final ReleaseGroupRepository releaseGroupRepository;
    private final ArtistImageRepository artistImageRepository;

    /**
     * Releases matching a query, Discogs first.
     *
     * Discogs is a marketplace for records, so the physical pressings people actually own
     * are its core data; MusicBrainz treats them as secondary and frequently has none at
     * all. MusicBrainz still answers when Discogs finds nothing or is unreachable, because
     * a degraded result beats an error and the two catalogues do not overlap perfectly.
     */
    @Cacheable(cacheNames = CacheConfig.METADATA_SEARCH, key = "#query + '|' + #limit")
    @Transactional
    public List<ReleaseDto> search(String query, int limit) {
        List<ReleaseDto> fromDiscogs = fromDiscogs(() -> discogsClient.search(query, limit));
        if (!fromDiscogs.isEmpty()) {
            return fromDiscogs;
        }
        log.debug("Discogs had nothing for '{}'; falling back to MusicBrainz", query);
        return musicBrainzClient.searchReleases(query, limit).stream()
                .map(this::upsert)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Runs a Discogs query and persists what comes back, swallowing an unreachable upstream.
     *
     * A failure here is never the end of a search: MusicBrainz is still there, and Discogs
     * times out under load often enough that letting it take the whole request down would
     * be the wrong trade.
     */
    private List<ReleaseDto> fromDiscogs(
            java.util.function.Supplier<List<DiscogsResponses.SearchResult>> query) {
        try {
            return query.get().stream()
                    .map(this::upsertDiscogs)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (UpstreamUnavailableException e) {
            log.warn("Discogs is unreachable, falling back to MusicBrainz: {}", e.getMessage());
            return List.of();
        }
    }

    @Transactional
    public List<ReleaseDto> findByBarcode(String barcode) {
        List<ReleaseEntity> known = releaseRepository.findAllByBarcode(barcode);
        if (!known.isEmpty()) {
            log.debug("Barcode {} served from the local mirror ({} releases)", barcode, known.size());
            return known.stream().map(this::toDto).toList();
        }
        // A barcode is the one identifier printed on the sleeve, and Discogs indexes far
        // more physical pressings by it.
        List<ReleaseDto> fromDiscogs = fromDiscogs(() -> discogsClient.findByBarcode(barcode));
        if (!fromDiscogs.isEmpty()) {
            return fromDiscogs;
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
     * One artist's portrait, resolved once and then free.
     *
     * <p>Two upstream calls the first time: MusicBrainz's {@code discogs} URL relation says
     * which Discogs artist this is, and that artist carries the pictures. Matching the two
     * databases on name would need neither call and would be wrong often enough to matter —
     * MusicBrainz holds at least three artists called "Daughter", and putting one band's
     * face on another's row undoes the disambiguation the rest of the screen works to show.
     *
     * <p>Every outcome is written, including "there is no picture". Most of the cost of that
     * answer is the lookup that discovered the artist has no Discogs link at all, and a row
     * that will never have a portrait would otherwise re-pay it on every search.
     *
     * <p>An upstream failure is deliberately <em>not</em> cached: it is a statement about
     * MusicBrainz's afternoon, not about the artist. It surfaces as an empty portrait and
     * the next caller tries again.
     */
    @Transactional
    public ArtistImageDto artistImage(UUID mbid) {
        Optional<ArtistImageEntity> cached = artistImageRepository.findById(mbid);
        if (cached.isPresent()) {
            return new ArtistImageDto(cached.get().getImageUrl());
        }
        // Without a token Discogs returns no images at all, so the lookups would cost two
        // upstream calls to learn nothing. Nothing is written either: the day a token
        // appears, every artist should resolve rather than stay permanently blank.
        if (!discogsClient.servesImages()) {
            return new ArtistImageDto(null);
        }

        Optional<Long> discogsId;
        Optional<String> imageUrl;
        try {
            discogsId = musicBrainzClient.discogsArtistId(mbid);
            imageUrl = discogsId.flatMap(discogsClient::artistImageUrl);
        } catch (UpstreamUnavailableException e) {
            log.debug("Could not resolve a portrait for artist {}: {}", mbid, e.getMessage());
            return new ArtistImageDto(null);
        }

        ArtistImageEntity entity = new ArtistImageEntity();
        entity.setMbid(mbid);
        entity.setDiscogsArtistId(discogsId.orElse(null));
        entity.setImageUrl(imageUrl.orElse(null));
        entity.setFetchedAt(Instant.now());
        artistImageRepository.save(entity);
        return new ArtistImageDto(entity.getImageUrl());
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
    public List<ReleaseDto> releasesInGroup(String albumId, int limit) {
        ExternalRef ref = ExternalRef.parse(albumId);

        // The two catalogues share no identifiers, but they agree on what a record is
        // called — so artist and title is what bridges a MusicBrainz album to Discogs'
        // pressings of it. That bridge is the whole point: MusicBrainz lists two digital
        // releases of Fred again..'s "ten days" and no vinyl at all, while Discogs has four
        // vinyl pressings of it.
        Optional<ReleaseGroupEntity> album = releaseGroupRepository.findByExternalId(ref.toString());
        if (album.isPresent()) {
            List<ReleaseDto> pressings = fromDiscogs(() -> discogsClient.pressingsOf(
                    album.get().getArtistName(), album.get().getTitle(), limit));
            if (!pressings.isEmpty()) {
                return pressings;
            }
        }

        // Nothing on Discogs, or an album this server has never mirrored. Only a
        // MusicBrainz album can be paged by its own id.
        if (ref.source() != ReleaseSource.MUSICBRAINZ) {
            return List.of();
        }
        return musicBrainzClient.findReleasesInGroup(ref.id(), limit).stream()
                .map(this::upsert)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Full detail for one release, including its cover theme. The palette is sampled and
     * persisted on the first lookup and reused by every caller afterwards.
     */
    @Transactional
    public ReleaseDto getRelease(String releaseId) {
        ExternalRef ref = ExternalRef.parse(releaseId);
        ReleaseEntity entity = releaseRepository
                .findByExternalId(ref.toString())
                .or(() -> musicBrainzClient
                        .lookupRelease(ref.id())
                        .flatMap(this::persist))
                .orElseThrow(() -> new ReleaseNotFoundException(releaseId));

        // Skipped once we know there is no cover: without this the palette fetch runs on
        // every single lookup of an artless release, and always comes back empty.
        if (entity.getDominantColor() == null && !Boolean.FALSE.equals(entity.getHasCoverArt())) {
            applyCoverPalette(entity);
        }
        return toDto(entity);
    }

    /** Discogs' half of {@link #upsert}: mirror it once, then serve it from here. */
    private Optional<ReleaseDto> upsertDiscogs(DiscogsResponses.SearchResult result) {
        if (result.id() == null) {
            return Optional.empty();
        }
        String ref = DiscogsMapper.releaseRefOf(result);
        return releaseRepository
                .findByExternalId(ref)
                .or(() -> persistDiscogs(result, ref))
                .map(this::toDto);
    }

    private Optional<ReleaseEntity> persistDiscogs(DiscogsResponses.SearchResult result, String ref) {
        ReleaseGroupEntity group = ensureDiscogsAlbum(result);

        ReleaseEntity entity = new ReleaseEntity();
        entity.setId(UUID.randomUUID());
        entity.setExternalId(ref);
        entity.setReleaseGroupId(group.getId());
        entity.setTitle(DiscogsMapper.titleOf(result.title()));
        entity.setArtistName(DiscogsMapper.artistOf(result.title()));
        entity.setFormat(DiscogsMapper.formatOf(result));
        entity.setYear(result.year());
        entity.setLabel(DiscogsMapper.labelOf(result));
        entity.setCatalogNumber(result.catno());
        entity.setCountry(result.country());
        entity.setBarcode(DiscogsMapper.barcodeOf(result));
        // Discogs search does not carry a release date or a track count; the pressing table
        // shows what it has rather than inventing the rest.
        entity.setCoverArtUrl(DiscogsMapper.coverUrlOf(result));
        // Definite either way: Discogs told us outright, unlike the archive's constructed
        // URL that has to be probed.
        entity.setHasCoverArt(DiscogsMapper.coverUrlOf(result) != null);
        entity.setFetchedAt(Instant.now());
        return Optional.of(releaseRepository.save(entity));
    }

    private ReleaseGroupEntity ensureDiscogsAlbum(DiscogsResponses.SearchResult result) {
        String albumRef = DiscogsMapper.albumRefOf(result);
        return releaseGroupRepository.findByExternalId(albumRef).orElseGet(() -> {
            ReleaseGroupEntity group = new ReleaseGroupEntity();
            group.setId(UUID.randomUUID());
            group.setExternalId(albumRef);
            group.setTitle(DiscogsMapper.titleOf(result.title()));
            group.setArtistName(DiscogsMapper.artistOf(result.title()));
            group.setFirstReleaseYear(result.year());
            group.setFetchedAt(Instant.now());
            return releaseGroupRepository.save(group);
        });
    }

    private Optional<ReleaseDto> upsert(MusicBrainzResponses.Release release) {
        return releaseRepository
                .findByExternalId(ExternalRef.musicBrainz(release.id()).toString())
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
        entity.setExternalId(ExternalRef.musicBrainz(release.id()).toString());
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
        String groupRef = ExternalRef.musicBrainz(release.releaseGroup().id()).toString();
        return releaseGroupRepository.findByExternalId(groupRef).orElseGet(() -> {
            ReleaseGroupEntity group = new ReleaseGroupEntity();
            group.setId(UUID.randomUUID());
            group.setExternalId(groupRef);
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
        Optional<byte[]> thumbnail = fetchCoverThumbnail(entity);
        entity.setHasCoverArt(thumbnail.isPresent());

        thumbnail.flatMap(colorExtractor::extract).ifPresentOrElse(palette -> {
            entity.setDominantColor(palette.dominantColor());
            entity.setAccentColor(palette.accentColor());
            entity.setLightness(palette.lightness());
            log.debug("Sampled cover for {}: {} (lightness {})",
                    entity.getExternalId(), palette.dominantColor(), palette.lightness());
        }, () -> log.debug("No cover art for release {}", entity.getExternalId()));

        releaseRepository.save(entity);
    }

    /**
     * The bytes to sample, from whichever archive actually holds this pressing's cover.
     *
     * The Cover Art Archive is keyed by MusicBrainz mbid, so a Discogs pressing is not in
     * it — its cover is the CDN URL the search already handed us. Leaving Discogs rows
     * unsampled was worse than a missing theme: with no palette the row stayed "unknown"
     * for ever, and every single detail open re-ran the lookup that was meant to happen
     * once. Search results are Discogs-first, so that was most of the collection.
     */
    private Optional<byte[]> fetchCoverThumbnail(ReleaseEntity entity) {
        ExternalRef ref = ExternalRef.parse(entity.getExternalId());
        if (ref.source() == ReleaseSource.MUSICBRAINZ) {
            return coverArtClient.fetchThumbnail(ref.id());
        }
        return discogsClient.fetchImage(entity.getCoverArtUrl());
    }

    private ReleaseDto toDto(ReleaseEntity entity) {
        String albumId = releaseGroupRepository
                .findById(entity.getReleaseGroupId())
                .map(ReleaseGroupEntity::getExternalId)
                .orElse(null);
        return MetadataMapper.toDto(entity, albumId);
    }
}
