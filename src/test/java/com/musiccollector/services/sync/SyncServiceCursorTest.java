package com.musiccollector.services.sync;

import com.musiccollector.entity.CopyEntity;
import com.musiccollector.entity.WishlistItemEntity;
import com.musiccollector.model.core.SyncPullDto;
import com.musiccollector.repository.CopyRepository;
import com.musiccollector.repository.WishlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * The pull cursor has to satisfy one invariant: it must never advance past a record the
 * response did not include. Getting that wrong strands records on the server with the
 * client believing it is up to date.
 */
@ExtendWith(MockitoExtension.class)
class SyncServiceCursorTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock private CopyRepository copyRepository;
    @Mock private WishlistItemRepository wishlistItemRepository;

    private SyncService service;

    @BeforeEach
    void setUp() {
        service = new SyncService(copyRepository, wishlistItemRepository, new ObjectMapper());
    }

    private CopyEntity copy(long seq) {
        CopyEntity entity = new CopyEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(USER);
        entity.setReleaseMbid("rel");
        entity.setCurrency("EUR");
        entity.setCreatedAt(1L);
        entity.setFieldClocks("{}");
        entity.setSyncSeq(seq);
        return entity;
    }

    private WishlistItemEntity wish(long seq) {
        WishlistItemEntity entity = new WishlistItemEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(USER);
        entity.setReleaseGroupMbid("group");
        entity.setTitle("T");
        entity.setArtistName("A");
        entity.setCreatedAt(1L);
        entity.setFieldClocks("{}");
        entity.setSyncSeq(seq);
        return entity;
    }

    private void given(List<CopyEntity> copies, List<WishlistItemEntity> wishes) {
        when(copyRepository.findAllByUserIdAndSyncSeqGreaterThanOrderBySyncSeqAsc(any(), anyLong()))
                .thenReturn(copies);
        when(wishlistItemRepository.findAllByUserIdAndSyncSeqGreaterThanOrderBySyncSeqAsc(any(), anyLong()))
                .thenReturn(wishes);
    }

    @Test
    void sendsBothKindsWhenEverythingFits() {
        // The regression: clamping the cursor to the lower kind withheld the copy at seq 9
        // while reporting hasMore=false, so the client stopped and never asked again.
        given(List.of(copy(9)), List.of(wish(4)));

        SyncPullDto page = service.pull(USER, 0);

        assertThat(page.copies()).hasSize(1);
        assertThat(page.wishes()).hasSize(1);
        assertThat(page.cursor()).isEqualTo(9);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void sendsBothKindsWhenTheWishIsTheLaterOne() {
        given(List.of(copy(3)), List.of(wish(11)));

        SyncPullDto page = service.pull(USER, 0);

        assertThat(page.copies()).hasSize(1);
        assertThat(page.wishes()).hasSize(1);
        assertThat(page.cursor()).isEqualTo(11);
    }

    @Test
    void keepsTheCursorWhereItWasWhenNothingChanged() {
        given(List.of(), List.of());

        SyncPullDto page = service.pull(USER, 42);

        assertThat(page.copies()).isEmpty();
        assertThat(page.wishes()).isEmpty();
        assertThat(page.cursor()).isEqualTo(42);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void holdsTheCursorBackAndAsksForAnotherPageWhenOneKindIsTruncated() {
        List<CopyEntity> manyCopies = new java.util.ArrayList<>();
        for (long seq = 1; seq <= 600; seq++) {
            manyCopies.add(copy(seq));
        }
        given(manyCopies, List.of(wish(900)));

        SyncPullDto page = service.pull(USER, 0);

        // The cursor stops at the last copy actually sent, and the far-later wish is held
        // back rather than being skipped over.
        assertThat(page.cursor()).isEqualTo(500);
        assertThat(page.copies()).hasSize(500);
        assertThat(page.wishes()).isEmpty();
        assertThat(page.hasMore()).isTrue();
    }
}
