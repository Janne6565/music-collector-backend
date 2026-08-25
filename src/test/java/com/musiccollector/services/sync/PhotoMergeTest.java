package com.musiccollector.services.sync;

import com.musiccollector.model.core.SyncPhotoDto;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoMergeTest {

    private static final String OLD = "000000000001000:0000:a";

    private static Map<String, String> clocks(String... overrides) {
        Map<String, String> map = new HashMap<>();
        for (String field : PhotoMerge.MERGEABLE_FIELDS) {
            map.put(field, OLD);
        }
        for (int i = 0; i < overrides.length; i += 2) {
            map.put(overrides[i], overrides[i + 1]);
        }
        return map;
    }

    private static SyncPhotoDto wishPhoto(String wishId, Map<String, String> clocks) {
        return new SyncPhotoDto(
                "p1", null, wishId, "user/p1", "image/jpeg", 2048L, 0, 1000L, null, clocks);
    }

    private static SyncPhotoDto photo(int sortIndex, Long deletedAt, Map<String, String> clocks) {
        return new SyncPhotoDto(
                "p1", "c1", null, "user/p1", "image/jpeg", 2048L, sortIndex, 1000L, deletedAt, clocks);
    }

    @Test
    void aLaterReorderWins() {
        SyncPhotoDto local = photo(0, null, clocks());
        SyncPhotoDto remote = photo(3, null, clocks("sortIndex", "000000000009000:0000:b"));

        assertThat(PhotoMerge.merge(local, remote).sortIndex()).isEqualTo(3);
    }

    @Test
    void aLaterDeleteWins() {
        SyncPhotoDto alive = photo(0, null, clocks());
        SyncPhotoDto deleted = photo(0, 9000L, clocks("deletedAt", "000000000009000:0000:b"));

        assertThat(PhotoMerge.merge(alive, deleted).deletedAt()).isEqualTo(9000L);
    }

    @Test
    void aReorderDoesNotResurrectADeletedPhoto() {
        // Reordering the strip on one device must not undo a delete made on another.
        SyncPhotoDto deleted = photo(0, 9000L, clocks("deletedAt", "000000000009000:0000:a"));
        SyncPhotoDto reordered = photo(5, null, clocks("sortIndex", "000000000009500:0000:b"));

        SyncPhotoDto merged = PhotoMerge.merge(deleted, reordered);

        assertThat(merged.sortIndex()).isEqualTo(5);
        assertThat(merged.deletedAt()).isEqualTo(9000L);
    }

    @Test
    void isCommutativeAndIdempotent() {
        SyncPhotoDto local = photo(1, null, clocks("sortIndex", "000000000009000:0000:a"));
        SyncPhotoDto remote = photo(2, 8000L, clocks("deletedAt", "000000000008000:0000:b"));

        SyncPhotoDto once = PhotoMerge.merge(local, remote);

        assertThat(PhotoMerge.merge(remote, local)).isEqualTo(once);
        assertThat(PhotoMerge.merge(once, remote)).isEqualTo(once);
        assertThat(PhotoMerge.merge(once, once)).isEqualTo(once);
    }

    @Test
    void takesTheEarlierCreationTime() {
        SyncPhotoDto early = new SyncPhotoDto("p1", "c1", null, "k", "image/png", 1L, 0, 300L, null, clocks());
        SyncPhotoDto late = new SyncPhotoDto("p1", "c1", null, "k", "image/png", 1L, 0, 900L, null, clocks());

        assertThat(PhotoMerge.merge(early, late).createdAt()).isEqualTo(300L);
    }

    @Test
    void keepsWhicheverSideExists() {
        SyncPhotoDto only = photo(0, null, clocks());

        assertThat(PhotoMerge.merge(only, null)).isEqualTo(only);
        assertThat(PhotoMerge.merge(null, only)).isEqualTo(only);
    }

    @Test
    void refusesToMergeTwoDifferentPhotos() {
        SyncPhotoDto one = photo(0, null, clocks());
        SyncPhotoDto other = new SyncPhotoDto("p2", "c1", null, "k", "image/png", 1L, 0, 1L, null, clocks());

        assertThatThrownBy(() -> PhotoMerge.merge(one, other)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPhotoCanBeReParentedFromACopyToAWish() {
        // The owner is a mergeable field like any other, which is what lets a picture
        // belong to a wishlist entry at all.
        SyncPhotoDto local = photo(0, null, clocks());
        SyncPhotoDto remote = wishPhoto(
                "w1", clocks("copyId", "000000000009000:0000:b", "wishId", "000000000009000:0000:b"));

        SyncPhotoDto merged = PhotoMerge.merge(local, remote);

        assertThat(merged.wishId()).isEqualTo("w1");
        assertThat(merged.copyId()).isNull();
    }

    @Test
    void anOlderDeviceThatNeverHeardOfWishesDoesNotUnsetTheOwner() {
        // A client one version behind pushes wishId as null with no clock for it. A field
        // nobody has stamped must not win over one somebody did.
        SyncPhotoDto mine = wishPhoto("w1", clocks("wishId", "000000000009000:0000:a"));
        SyncPhotoDto theirs = photo(0, null, clocks());

        assertThat(PhotoMerge.merge(mine, theirs).wishId()).isEqualTo("w1");
        assertThat(PhotoMerge.merge(theirs, mine).wishId()).isEqualTo("w1");
    }
}
