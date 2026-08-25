package com.musiccollector.services.auth;

import com.musiccollector.entity.ConsentEntity;
import com.musiccollector.model.core.ConsentDocument;
import com.musiccollector.model.core.ConsentDto;
import com.musiccollector.repository.ConsentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Records what an account agreed to, so that Art. 7 Abs. 1 DSGVO can be answered later.
 *
 * <p>The version is read from {@link ConsentDocument} rather than from whatever the client
 * sent. A client only ever says <em>that</em> the boxes were ticked; which document that
 * was is a fact about the server at that moment, and the one thing a stale app must not be
 * able to get wrong.
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    private static final Logger log = LoggerFactory.getLogger(ConsentService.class);

    /**
     * What a new account agrees to. The sign-up screen collects it as two ticks -- the
     * agreement covers the terms and the privacy policy together, the age confirmation
     * stands alone -- but three separate statements are recorded, because that is what has
     * to be shown one at a time later.
     */
    private static final List<ConsentDocument> AT_SIGN_UP =
            List.of(ConsentDocument.TERMS, ConsentDocument.PRIVACY, ConsentDocument.AGE);

    private final ConsentRepository consentRepository;

    /**
     * Stamps the sign-up consents for a brand-new account.
     *
     * <p>MANDATORY: it is part of creating the account, never a write of its own. An account
     * that exists without its consent rows is exactly the state this table is here to rule
     * out, and a separate transaction is a way to end up in it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSignUp(UUID userId) {
        Instant now = Instant.now();
        for (ConsentDocument document : AT_SIGN_UP) {
            ConsentEntity consent = new ConsentEntity();
            consent.setId(UUID.randomUUID());
            consent.setUserId(userId);
            consent.setDocument(document);
            consent.setVersion(document.currentVersion());
            consent.setAcceptedAt(now);
            consentRepository.save(consent);
        }
        log.debug("Recorded sign-up consents for user {}", userId);
    }

    @Transactional(readOnly = true)
    public List<ConsentDto> list(UUID userId) {
        return consentRepository.findAllByUserIdOrderByAcceptedAtDesc(userId).stream()
                .map(consent -> new ConsentDto(consent.getDocument(), consent.getVersion(), consent.getAcceptedAt()))
                .toList();
    }
}
