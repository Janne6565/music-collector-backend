package com.musiccollector.services.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends account mail once the change it describes is actually committed.
 *
 * <p>{@link TransactionPhase#AFTER_COMMIT} is the default and is stated anyway, because it
 * is the entire reason this class exists rather than a direct call.
 */
@Component
public class AccountMailListener {

    private static final Logger log = LoggerFactory.getLogger(AccountMailListener.class);

    /**
     * Apple and Google may withhold an address; those accounts get a unique placeholder so
     * the column can stay NOT NULL. It is not a mailbox, and posting to it would bounce
     * against a domain reserved by RFC 2606 on every single account mail.
     */
    private static final String PLACEHOLDER_DOMAIN = "@no-email.invalid";

    private final AccountMailer mailer;

    public AccountMailListener(AccountMailer mailer) {
        this.mailer = mailer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(AccountMailEvent event) {
        if (event.recipient() == null || event.recipient().endsWith(PLACEHOLDER_DOMAIN)) {
            log.debug("No real address on this account; {} not sent", event.getClass().getSimpleName());
            return;
        }
        switch (event) {
            case AccountMailEvent.PasswordResetRequested e -> mailer.passwordReset(e.recipient(), e.token());
            case AccountMailEvent.EmailConfirmationRequested e -> mailer.confirmEmail(e.recipient(), e.token());
            case AccountMailEvent.EmailChangeRequested e -> mailer.confirmNewAddress(e.recipient(), e.token());
            case AccountMailEvent.EmailChangeStarted e ->
                mailer.emailChangeStarted(e.recipient(), e.newEmail(), e.cancelToken(), e.at());
            case AccountMailEvent.PasswordChanged e -> mailer.passwordChanged(e.recipient(), e.at());
            case AccountMailEvent.SignInMethodLinked e ->
                mailer.signInMethodLinked(e.recipient(), e.provider(), e.at());
            case AccountMailEvent.AccountDeleted e -> mailer.accountDeleted(e.recipient(), e.copies());
        }
    }
}
