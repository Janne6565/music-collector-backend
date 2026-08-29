package com.rekordo.services.storage;

import com.rekordo.configuration.StorageProperties;
import com.rekordo.model.core.StorageUsageDto;
import com.rekordo.model.exception.StorageQuotaExceededException;
import com.rekordo.repository.PhotoRepository;
import com.rekordo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The storage allowance: how much of it an account has spent, and whether one more upload
 * fits.
 *
 * <p>Counted from the database rather than from the bucket, and the difference matters.
 * {@link com.rekordo.services.metrics.StorageMetrics} walks MinIO because it is asking what
 * is being paid for, orphans included; this is asking what the person is responsible for,
 * and charging somebody for bytes a failed delete left behind would be charging them for
 * our bug. The two numbers are meant to agree, and the gauge next door is what says when
 * they stop.
 *
 * <p>Cover art is not in either sum: it comes from MusicBrainz and Discogs at display time
 * and is never stored.
 */
@Service
@RequiredArgsConstructor
public class StorageUsageService {

    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final StorageProperties properties;

    /** What the meter on Account shows. */
    @Transactional(readOnly = true)
    public StorageUsageDto usage(UUID userId) {
        PhotoRepository.Usage photos = photoRepository.sumLiveBytes(userId);
        long avatarBytes = avatarBytes(userId);
        return new StorageUsageDto(
                photos.getBytes(),
                photos.getPhotos(),
                avatarBytes,
                photos.getBytes() + avatarBytes,
                properties.quotaBytes());
    }

    /**
     * Refuses before anything is written when {@code incomingBytes} would not fit.
     *
     * <p>{@code replacingBytes} is what this upload takes the place of -- the row a photo id
     * already has, or the profile picture being written over. Both write to a key that is
     * derived from an id rather than a random one, so the old object is gone the moment the
     * new one lands, and charging for both would refuse an upload that costs nothing.
     *
     * <p>An account already over its allowance (an older app version's originals, uploaded
     * before this existed) is not made to delete anything. It simply cannot add, which is
     * what {@code used + incoming > quota} says on its own.
     */
    @Transactional(readOnly = true)
    public void requireRoom(UUID userId, long incomingBytes, long replacingBytes) {
        long used = usage(userId).usedBytes();
        long after = used - replacingBytes + incomingBytes;
        if (after > properties.quotaBytes()) {
            throw new StorageQuotaExceededException(used, properties.quotaBytes());
        }
    }

    /**
     * Null for a picture written before {@code users.avatar_bytes} existed, and counted as
     * nothing until it is next replaced -- see V37. It is a quarter of a percent of the
     * allowance, so the miscount can never be the reason an upload is refused.
     */
    private long avatarBytes(UUID userId) {
        return userRepository
                .findById(userId)
                .filter(user -> user.getAvatarKey() != null)
                .map(user -> user.getAvatarBytes() == null ? 0L : user.getAvatarBytes())
                .orElse(0L);
    }
}
