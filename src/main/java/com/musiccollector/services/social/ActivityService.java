package com.musiccollector.services.social;

import com.musiccollector.entity.ActivityEventEntity;
import com.musiccollector.entity.ReleaseEntity;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.core.ActivityActorDto;
import com.musiccollector.model.core.ActivityEntryDto;
import com.musiccollector.model.core.ActivityFeedDto;
import com.musiccollector.model.core.ActivityType;
import com.musiccollector.model.core.CopyOrigin;
import com.musiccollector.repository.ActivityEventRepository;
import com.musiccollector.repository.CopyRepository;
import com.musiccollector.repository.ReleaseGroupRepository;
import com.musiccollector.repository.ReleaseRepository;
import com.musiccollector.repository.WishlistItemRepository;
import com.musiccollector.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The Friends feed: what gets written, and what anybody is allowed to read back.
 *
 * <p>Two rules do most of the work here. Nothing is written unless the client said the
 * record was added by hand — silence is the default, so a client whose intent we cannot
 * read announces nothing. And nothing is read back without asking
 * {@link VisibilityService} again at request time, so closing a shelf takes the history
 * with it instead of leaving it scattered across other people's feeds.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    /** How many lines the feed hands back. */
    private static final int FEED_SIZE = 50;

    /**
     * Read wider than that before filtering. Events belonging to a shelf that has since
     * closed drop out here, and reading exactly fifty would return a short page whenever
     * one did.
     */
    private static final int SCAN_SIZE = 400;

    /**
     * Adds by one person inside this window become one line. Somebody cataloguing a crate
     * on a Sunday afternoon is one event in their friends' lives, not eleven.
     */
    private static final Duration COLLAPSE_WINDOW = Duration.ofHours(2);

    private static final int COLLAPSED_COVERS = 5;

    private final ActivityEventRepository activityRepository;
    private final CopyRepository copyRepository;
    private final ReleaseRepository releaseRepository;
    private final ReleaseGroupRepository releaseGroupRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final UserRepository userRepository;
    private final VisibilityService visibilityService;

    /**
     * Record that a copy was added by hand.
     *
     * <p>Called from the sync push for rows that did not exist before. An edit to an
     * existing copy pushes the same record again and must not announce it twice, which the
     * unique index on (actor, type, subject) enforces even if a caller forgets.
     *
     * @param occurredAt the device's own clock, in epoch millis
     */
    @Transactional
    public void recordCopyAdded(
            UUID actorId, UUID copyId, CopyOrigin origin, String releaseId, String title, String artist, Long occurredAt) {
        if (origin != CopyOrigin.MANUAL) {
            // An import, a first sign-in, or a client that did not say. All silent.
            return;
        }
        if (activityRepository.existsByActorIdAndTypeAndSubjectId(actorId, ActivityType.COPY_ADDED, copyId)
                || activityRepository.existsByActorIdAndTypeAndSubjectId(
                        actorId, ActivityType.WISH_FULFILLED, copyId)) {
            return;
        }
        // "Off the wishlist, onto the shelf" is a better line than "added a copy", and it is
        // the same event -- so which one this is depends on whether they were hunting for it.
        ActivityType type = wasWishedFor(actorId, releaseId) ? ActivityType.WISH_FULFILLED : ActivityType.COPY_ADDED;
        save(actorId, type, copyId, releaseId, title, artist, occurredAt);
    }

    /**
     * Whether this album was on their wishlist when the copy turned up.
     *
     * <p>The wish is keyed by album and the copy by release, so this walks the copy's
     * release up to its group. A hand-entered copy points at no catalogue row at all and
     * simply never matches, which is the right answer -- nobody wishes for a pressing that
     * exists in one person's collection.
     */
    private boolean wasWishedFor(UUID actorId, String releaseId) {
        if (releaseId == null || releaseId.startsWith("local:")) {
            return false;
        }
        return releaseRepository
                .findByExternalId(releaseId)
                .flatMap(release -> releaseGroupRepository.findById(release.getReleaseGroupId()))
                .map(group -> !wishlistItemRepository
                        .findAllByUserIdAndAlbumIdAndDeletedAtIsNull(actorId, group.getExternalId())
                        .isEmpty())
                .orElse(false);
    }

    @Transactional
    public void recordWishAdded(UUID actorId, UUID wishId, String albumId, String title, String artist, Long occurredAt) {
        if (activityRepository.existsByActorIdAndTypeAndSubjectId(actorId, ActivityType.WISH_ADDED, wishId)) {
            return;
        }
        save(actorId, ActivityType.WISH_ADDED, wishId, albumId, title, artist, occurredAt);
    }

    @Transactional
    public void recordFriendshipAccepted(UUID actorId, UUID otherId) {
        if (activityRepository.existsByActorIdAndTypeAndSubjectId(
                actorId, ActivityType.FRIENDSHIP_ACCEPTED, otherId)) {
            return;
        }
        save(actorId, ActivityType.FRIENDSHIP_ACCEPTED, otherId, null, null, null, null);
    }

    /**
     * Forget everything said about one record.
     *
     * <p>Deleting a copy has to take its announcement with it. A line saying somebody added
     * a record they have since removed is a claim about them that is no longer true.
     */
    @Transactional
    public void forget(UUID actorId, UUID subjectId) {
        activityRepository.deleteAllByActorIdAndSubjectId(actorId, subjectId);
    }

    private void save(
            UUID actorId, ActivityType type, UUID subjectId, String releaseId, String title, String artist, Long occurredAt) {
        Instant now = Instant.now();
        // A copy typed in by hand carries its own title; a matched one carries none, and the
        // name comes from the mirror. Resolved once, here, rather than on every read -- the
        // mirror is a cache that may be evicted, and a feed line that loses its title six
        // months later is worse than one that cannot be re-resolved.
        if (isBlank(title) && releaseId != null && !releaseId.startsWith("local:")) {
            ReleaseEntity release = releaseRepository.findByExternalId(releaseId).orElse(null);
            if (release != null) {
                title = release.getTitle();
                artist = isBlank(artist) ? release.getArtistName() : artist;
            }
        }
        ActivityEventEntity event = new ActivityEventEntity();
        event.setId(UUID.randomUUID());
        event.setActorId(actorId);
        event.setType(type);
        event.setSubjectId(subjectId);
        event.setReleaseId(releaseId);
        event.setTitle(title);
        event.setArtistName(artist);
        event.setOccurredAt(clamp(occurredAt, now));
        event.setRecordedAt(now);
        activityRepository.save(event);
        log.debug("Recorded {} by {} about {}", type, actorId, subjectId);
    }

    /**
     * The device's own time, trusted — but never allowed to be in the future.
     *
     * <p>Trusting the client is the right call for a local-first app: a copy added on a
     * plane and synced two days later belongs where the person put it, not where the server
     * first heard of it. What that cannot be allowed to buy is a permanent place at the top
     * of every friend's feed, which is all this clamp prevents.
     */
    private static Instant clamp(Long occurredAt, Instant now) {
        if (occurredAt == null) {
            return now;
        }
        Instant claimed = Instant.ofEpochMilli(occurredAt);
        return claimed.isAfter(now) ? now : claimed;
    }

    /**
     * The feed, as one viewer is allowed to see it.
     *
     * <p>Visibility is applied here, on the way out, rather than when the event was written.
     * That is what makes "Only me" reach backwards: the lines do not have to be found and
     * deleted from anywhere, they simply stop being readable.
     */
    @Transactional(readOnly = true)
    public ActivityFeedDto feed(UUID viewerId, Collection<UUID> friendIds) {
        List<UUID> actorIds = new ArrayList<>(friendIds);
        if (actorIds.isEmpty()) {
            return new ActivityFeedDto(List.of());
        }
        // Your own accepted-request lines belong in your feed too: "Anna Reuter accepted
        // your request" is news to you and to nobody else.
        actorIds.add(viewerId);

        List<ActivityEventEntity> events =
                activityRepository.feedFor(actorIds, PageRequest.of(0, SCAN_SIZE));
        List<ActivityEventEntity> readable = events.stream()
                .filter(event -> mayRead(viewerId, event))
                .toList();

        Map<UUID, UserEntity> actors = actorsOf(readable);
        Map<String, ReleaseEntity> releases = releasesOf(readable);
        return new ActivityFeedDto(collapse(readable, actors, releases));
    }

    /**
     * What one viewer is allowed to see from the last stretch — the Sunday digest's window.
     *
     * <p>Visibility is applied here, at send time, exactly as it is for the feed. That is
     * the whole reason the digest reads through this service rather than the repository: a
     * shelf closed on Saturday must not turn up in Sunday's mail, and a mail is the one copy
     * of a claim that cannot be taken back.
     *
     * <p>Only what somebody put on a shelf. Accepted requests are personal news and belong
     * in the feed, not in a summary about other people's records; wishes are not records.
     */
    @Transactional(readOnly = true)
    public List<ActivityEntryDto> since(UUID viewerId, Collection<UUID> friendIds, Instant since) {
        if (friendIds.isEmpty()) {
            return List.of();
        }
        List<ActivityEventEntity> events = activityRepository
                .feedSince(List.copyOf(friendIds), since, PageRequest.of(0, SCAN_SIZE))
                .stream()
                .filter(event -> event.getType() == ActivityType.COPY_ADDED
                        || event.getType() == ActivityType.WISH_FULFILLED)
                .filter(event -> mayRead(viewerId, event))
                .toList();

        Map<UUID, UserEntity> actors = actorsOf(events);
        Map<String, ReleaseEntity> releases = releasesOf(events);
        return collapse(events, actors, releases);
    }

    private boolean mayRead(UUID viewerId, ActivityEventEntity event) {
        return switch (event.getType()) {
            // Somebody's own news, and only theirs. An accepted request is not activity
            // their other friends have any business seeing.
            case FRIENDSHIP_ACCEPTED -> event.getActorId().equals(viewerId)
                    || Objects.equals(event.getSubjectId(), viewerId);
            case WISH_ADDED -> visibilityService.canSeeWishlist(viewerId, event.getActorId());
            case COPY_ADDED, WISH_FULFILLED -> visibilityService.canSeeCollection(viewerId, event.getActorId())
                    && copyStillShown(event);
        };
    }

    /**
     * A line about a copy survives only as long as the copy does.
     *
     * <p>Deleting is handled by {@link #forget}, but hiding one copy is a mergeable field
     * that arrives through sync with no hook of its own, so the feed checks it here.
     */
    private boolean copyStillShown(ActivityEventEntity event) {
        if (event.getSubjectId() == null) {
            return true;
        }
        return copyRepository
                .findById(event.getSubjectId())
                .filter(copy -> copy.getDeletedAt() == null)
                .filter(copy -> !copy.isHidden())
                .isPresent();
    }

    /**
     * Fold a burst of adds by one person into a single line.
     *
     * <p>Only adjacent events by the same actor within the window, which keeps this a
     * rendering decision rather than something baked into the stored rows — the window can
     * change without a migration.
     */
    private List<ActivityEntryDto> collapse(
            List<ActivityEventEntity> events, Map<UUID, UserEntity> actors, Map<String, ReleaseEntity> releases) {
        List<ActivityEntryDto> entries = new ArrayList<>();
        int index = 0;
        while (index < events.size() && entries.size() < FEED_SIZE) {
            ActivityEventEntity head = events.get(index);
            int end = index + 1;
            if (head.getType() == ActivityType.COPY_ADDED) {
                while (end < events.size()
                        && events.get(end).getType() == ActivityType.COPY_ADDED
                        && events.get(end).getActorId().equals(head.getActorId())
                        && Duration.between(events.get(end).getOccurredAt(), head.getOccurredAt())
                                .compareTo(COLLAPSE_WINDOW)
                                <= 0) {
                    end++;
                }
            }
            List<ActivityEventEntity> group = events.subList(index, end);
            entries.add(toDto(head, group, actors, releases));
            index = end;
        }
        return entries;
    }

    private ActivityEntryDto toDto(
            ActivityEventEntity head,
            List<ActivityEventEntity> group,
            Map<UUID, UserEntity> actors,
            Map<String, ReleaseEntity> releases) {
        ReleaseEntity release = head.getReleaseId() == null ? null : releases.get(head.getReleaseId());
        List<String> covers = group.size() > 1
                ? group.stream()
                        .limit(COLLAPSED_COVERS)
                        .map(event -> coverOf(event, releases))
                        .filter(url -> url != null)
                        .toList()
                : List.of();
        return new ActivityEntryDto(
                head.getId(),
                head.getType(),
                actorOf(head, actors),
                head.getTitle(),
                head.getArtistName(),
                head.getReleaseId(),
                release == null ? null : release.getFormat(),
                release == null ? null : release.getYear(),
                coverOf(head, releases),
                head.getOccurredAt(),
                group.size(),
                covers);
    }

    private ActivityActorDto actorOf(ActivityEventEntity event, Map<UUID, UserEntity> actors) {
        UserEntity actor = actors.get(event.getActorId());
        return actor == null
                ? new ActivityActorDto(event.getActorId(), null, null)
                : new ActivityActorDto(actor.getId(), actor.getHandle(), actor.getDisplayName());
    }

    private String coverOf(ActivityEventEntity event, Map<String, ReleaseEntity> releases) {
        ReleaseEntity release = event.getReleaseId() == null ? null : releases.get(event.getReleaseId());
        if (release == null || Boolean.FALSE.equals(release.getHasCoverArt())) {
            return null;
        }
        return release.getCoverArtUrl();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Map<UUID, UserEntity> actorsOf(List<ActivityEventEntity> events) {
        Set<UUID> ids = new HashSet<>();
        events.forEach(event -> ids.add(event.getActorId()));
        Map<UUID, UserEntity> actors = new HashMap<>();
        if (ids.isEmpty()) {
            return actors;
        }
        for (UserEntity user : userRepository.findAllByIdIn(ids)) {
            actors.put(user.getId(), user);
        }
        return actors;
    }

    private Map<String, ReleaseEntity> releasesOf(List<ActivityEventEntity> events) {
        Set<String> ids = new HashSet<>();
        for (ActivityEventEntity event : events) {
            if (event.getReleaseId() != null && !event.getReleaseId().startsWith("local:")) {
                ids.add(event.getReleaseId());
            }
        }
        Map<String, ReleaseEntity> releases = new HashMap<>();
        if (ids.isEmpty()) {
            return releases;
        }
        for (ReleaseEntity release : releaseRepository.findAllByExternalIdIn(ids)) {
            releases.put(release.getExternalId(), release);
        }
        return releases;
    }
}
