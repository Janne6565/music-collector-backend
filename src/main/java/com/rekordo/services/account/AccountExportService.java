package com.rekordo.services.account;

import com.rekordo.entity.FriendshipEntity;
import com.rekordo.entity.OAuthIdentityEntity;
import com.rekordo.entity.UserEntity;
import com.rekordo.model.core.AccountExportDto;
import com.rekordo.model.core.SyncCopyDto;
import com.rekordo.model.core.SyncPhotoDto;
import com.rekordo.model.core.SyncPullDto;
import com.rekordo.model.core.SyncWishDto;
import com.rekordo.repository.OAuthIdentityRepository;
import com.rekordo.repository.UserRepository;
import com.rekordo.services.auth.ConsentService;
import com.rekordo.services.notifications.NotificationPreferenceService;
import com.rekordo.services.social.FriendshipService;
import com.rekordo.services.social.SharingService;
import com.rekordo.services.storage.AvatarService;
import com.rekordo.services.sync.SyncService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The Art. 15 / Art. 20 export.
 *
 * <p>It reads through the same services the app does rather than going at the tables
 * directly. That is the point: an export assembled from its own queries drifts from what the
 * app actually stores, and the first person to notice is the one who asked for their data.
 */
@Service
@RequiredArgsConstructor
public class AccountExportService {

    private static final Logger log = LoggerFactory.getLogger(AccountExportService.class);

    /**
     * How many pull pages an export will follow before giving up.
     *
     * <p>A pull is bounded, so a large collection arrives in pages, and the loop below walks
     * them. The bound is not about collection size -- 500 pages is a million records -- it is
     * there so that a cursor which somehow stops advancing ends the request instead of the
     * server.
     */
    private static final int MAX_PAGES = 500;

    private final UserRepository userRepository;
    private final SyncService syncService;
    private final ConsentService consentService;
    private final SharingService sharingService;
    private final FriendshipService friendshipService;
    private final OAuthIdentityRepository oauthIdentityRepository;
    private final NotificationPreferenceService notificationPreferenceService;

    @Transactional(readOnly = true)
    public AccountExportDto export(UserEntity user) {
        List<SyncCopyDto> copies = new ArrayList<>();
        List<SyncWishDto> wishes = new ArrayList<>();
        List<SyncPhotoDto> photos = new ArrayList<>();

        long cursor = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            SyncPullDto pull = syncService.pull(user.getId(), cursor);
            copies.addAll(pull.copies());
            wishes.addAll(pull.wishes());
            photos.addAll(pull.photos());
            // A cursor that did not move cannot move on the next round either, so stopping
            // here is the difference between a truncated export and an endless one.
            if (!pull.hasMore() || pull.cursor() <= cursor) {
                break;
            }
            cursor = pull.cursor();
        }

        log.debug("Exported {} copies for user {}", copies.size(), user.getId());
        return new AccountExportDto(
                Instant.now(),
                new AccountExportDto.AccountDto(
                        user.getId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getHandle(),
                        AvatarService.urlFor(user),
                        user.getCreatedAt()),
                consentService.list(user.getId()),
                sharingService.read(user.getId()),
                // Defaults included: a preference nobody ever changed is still a fact held
                // about them, and an export that only listed the edited rows would read as
                // though the rest had no answer.
                notificationPreferenceService.forUser(user).categories(),
                copies,
                wishes,
                photos,
                friends(user.getId()),
                providers(user.getId()));
    }

    /**
     * Everyone this account is connected to, requests in both directions included -- a
     * pending request is a fact about the person who sent it as much as one they received.
     */
    private List<AccountExportDto.FriendExportDto> friends(UUID userId) {
        List<FriendshipEntity> all = new ArrayList<>(friendshipService.accepted(userId));
        all.addAll(friendshipService.incoming(userId));
        all.addAll(friendshipService.outgoing(userId));
        return all.stream()
                .map(friendship -> {
                    UUID otherId = friendship.getRequesterId().equals(userId)
                            ? friendship.getAddresseeId()
                            : friendship.getRequesterId();
                    return userRepository
                            .findById(otherId)
                            .map(other -> new AccountExportDto.FriendExportDto(
                                    other.getHandle(),
                                    other.getDisplayName(),
                                    friendship.getStatus().name(),
                                    friendship.getCreatedAt()))
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** Which buttons this account can sign in with. Names only -- a token is not export data. */
    private List<String> providers(UUID userId) {
        return oauthIdentityRepository.findAllByUserId(userId).stream()
                .map(OAuthIdentityEntity::getProvider)
                .distinct()
                .toList();
    }
}
