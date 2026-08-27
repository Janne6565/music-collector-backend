package com.rekordo.services.auth;

import com.rekordo.entity.ConsentEntity;
import com.rekordo.model.core.ConsentDocument;
import com.rekordo.repository.ConsentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * What a sign-up leaves behind, which is the only thing that can answer Art. 7 Abs. 1 later.
 */
@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock private ConsentRepository consentRepository;

    @Test
    void recordsAllThreeStatementsAtTheVersionTheServerHolds() {
        ConsentService service = new ConsentService(consentRepository);
        UUID userId = UUID.randomUUID();

        service.recordSignUp(userId);

        ArgumentCaptor<ConsentEntity> saved = ArgumentCaptor.forClass(ConsentEntity.class);
        verify(consentRepository, times(3)).save(saved.capture());
        List<ConsentEntity> rows = saved.getAllValues();

        // Two ticks on the screen, three statements in the record: the agreement covers the
        // terms and the privacy policy, and each has to be shown on its own afterwards.
        assertThat(rows)
                .extracting(ConsentEntity::getDocument)
                .containsExactly(ConsentDocument.TERMS, ConsentDocument.PRIVACY, ConsentDocument.AGE);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getUserId()).isEqualTo(userId);
            assertThat(row.getId()).isNotNull();
            assertThat(row.getAcceptedAt()).isNotNull();
            assertThat(row.getVersion()).isEqualTo(row.getDocument().currentVersion());
        });
        // One moment, not three: they were ticked on one screen and submitted together.
        assertThat(rows).extracting(ConsentEntity::getAcceptedAt).containsOnly(rows.getFirst().getAcceptedAt());
    }
}
