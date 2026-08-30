package com.rekordo.services.sync;

import com.rekordo.model.core.SyncWishDto;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WishMergeTest {

    private static final String OLD = "000000000001000:0000:a";

    private static Map<String, String> clocks(String... overrides) {
        Map<String, String> map = new HashMap<>();
        for (String field : WishMerge.MERGEABLE_FIELDS) {
            map.put(field, OLD);
        }
        for (int i = 0; i < overrides.length; i += 2) {
            map.put(overrides[i], overrides[i + 1]);
        }
        return map;
    }

    private static SyncWishDto wish(String note, String format, Long deletedAt, Map<String, String> clocks) {
        return new SyncWishDto(
                "w1",
                "group-1",
                "discogs:1",
                null,
                "Ege Bamyasi",
                "Can",
                1972,
                format,
                note,
                null,
                1000L,
                deletedAt,
                clocks);
    }

    @Test
    void keepsEditsToDifferentFieldsFromBothDevices() {
        SyncWishDto local = wish("Want an original Spoon press", "VINYL", null,
                clocks("note", "000000000009000:0000:a"));
        SyncWishDto remote = wish("older note", "CASSETTE", null,
                clocks("desiredFormat", "000000000008000:0000:b"));

        SyncWishDto merged = WishMerge.merge(local, remote);

        assertThat(merged.note()).isEqualTo("Want an original Spoon press");
        assertThat(merged.desiredFormat()).isEqualTo("CASSETTE");
    }

    @Test
    void isCommutativeAndIdempotent() {
        SyncWishDto local = wish("mine", "VINYL", null, clocks("note", "000000000009000:0000:a"));
        SyncWishDto remote = wish("theirs", "CD", null, clocks("desiredFormat", "000000000008000:0000:b"));

        SyncWishDto once = WishMerge.merge(local, remote);

        assertThat(WishMerge.merge(remote, local)).isEqualTo(once);
        assertThat(WishMerge.merge(once, remote)).isEqualTo(once);
        assertThat(WishMerge.merge(once, once)).isEqualTo(once);
    }

    @Test
    void theHandSortedPositionIsAnOrdinaryMergeableField() {
        SyncWishDto here = new SyncWishDto(
                "w1", "g", null, null, "T", "A", null, null, null, 0, 1L, null,
                clocks("sortIndex", "000000000009000:0000:a"));
        SyncWishDto there = new SyncWishDto(
                "w1", "g", null, null, "T", "A", null, null, null, 7, 1L, null,
                clocks("sortIndex", "000000000008000:0000:b"));

        assertThat(WishMerge.merge(here, there).sortIndex()).isEqualTo(0);
    }

    @Test
    void aLaterDeleteWins() {
        SyncWishDto alive = wish("n", "VINYL", null, clocks());
        SyncWishDto deleted = wish("n", "VINYL", 9000L, clocks("deletedAt", "000000000009000:0000:b"));

        assertThat(WishMerge.merge(alive, deleted).deletedAt()).isEqualTo(9000L);
    }

    @Test
    void aLaterEditBeatsAnEarlierDelete() {
        // Re-adding a wish on one device after deleting it on another must not lose the wish.
        SyncWishDto deleted = wish("n", "VINYL", 5000L, clocks("deletedAt", "000000000005000:0000:a"));
        SyncWishDto revived = wish("n", "VINYL", null, clocks("deletedAt", "000000000009000:0000:b"));

        assertThat(WishMerge.merge(deleted, revived).deletedAt()).isNull();
    }

    @Test
    void takesTheEarlierCreationTime() {
        SyncWishDto early = new SyncWishDto("w1", "g", null, null, "T", "A", null, null, null, null, 300L, null, clocks());
        SyncWishDto late = new SyncWishDto("w1", "g", null, null, "T", "A", null, null, null, null, 900L, null, clocks());

        assertThat(WishMerge.merge(early, late).createdAt()).isEqualTo(300L);
    }

    @Test
    void thePickedPressingIsAnOrdinaryMergeableField() {
        // Two devices, two pressings of the same album: the later pick wins, and it wins on
        // its own clock rather than dragging the rest of the entry with it.
        SyncWishDto here = wish("n", "VINYL", null, clocks("releaseId", "000000000009000:0000:a"));
        SyncWishDto there = new SyncWishDto(
                "w1",
                "group-1",
                "discogs:2",
                null,
                "Ege Bamyasi",
                "Can",
                1972,
                "VINYL",
                "n",
                null,
                1000L,
                null,
                clocks("releaseId", "000000000008000:0000:b"));

        assertThat(WishMerge.merge(here, there).releaseId()).isEqualTo("discogs:1");
        assertThat(WishMerge.merge(there, here).releaseId()).isEqualTo("discogs:1");
    }

    @Test
    void aClientThatNamesNoPressingDoesNotEraseOne() {
        // An older client sends no `releaseId` at all, so its clock for the field is absent
        // -- which must read as "never said anything", not as "cleared it".
        Map<String, String> silent = clocks();
        silent.remove("releaseId");
        SyncWishDto older = new SyncWishDto(
                "w1", "group-1", null, null, "Ege Bamyasi", "Can", 1972, "VINYL", "n", null, 1000L, null, silent);
        SyncWishDto picked = wish("n", "VINYL", null, clocks());

        assertThat(WishMerge.merge(picked, older).releaseId()).isEqualTo("discogs:1");
    }

    @Test
    void refusesToMergeTwoDifferentWishes() {
        SyncWishDto one = wish("n", "VINYL", null, clocks());
        SyncWishDto other = new SyncWishDto("w2", "g", null, null, "T", "A", null, null, null, null, 1L, null, clocks());

        assertThatThrownBy(() -> WishMerge.merge(one, other)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsWishesThatExistOnOnlyOneSide() {
        SyncWishDto shared = wish("n", "VINYL", null, clocks());
        SyncWishDto onlyRemote = new SyncWishDto("w2", "g", null, null, "T", "A", null, null, null, null, 1L, null, clocks());

        List<SyncWishDto> merged = WishMerge.mergeAll(List.of(shared), List.of(shared, onlyRemote));

        assertThat(merged).extracting(SyncWishDto::id).containsExactlyInAnyOrder("w1", "w2");
    }
}
