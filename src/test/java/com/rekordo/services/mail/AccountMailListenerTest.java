package com.rekordo.services.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AccountMailListenerTest {

    @Mock private AccountMailer mailer;

    @Test
    void routesEachEventToItsMail() {
        AccountMailListener listener = new AccountMailListener(mailer);

        listener.on(new AccountMailEvent.PasswordResetRequested("jonas@example.test", "tok"));
        listener.on(new AccountMailEvent.AccountDeleted("jonas@example.test", 3));

        verify(mailer).passwordReset("jonas@example.test", "tok");
        verify(mailer).accountDeleted("jonas@example.test", 3);
    }

    @Test
    void neverPostsToAWithheldAddress() {
        // Apple and Google may refuse to hand over an address; those accounts carry a unique
        // placeholder so the column can stay NOT NULL. Mailing it would bounce every time.
        AccountMailListener listener = new AccountMailListener(mailer);

        listener.on(new AccountMailEvent.PasswordChanged("001abc@no-email.invalid", Instant.now()));
        listener.on(new AccountMailEvent.AccountDeleted("001abc@no-email.invalid", 3));

        verifyNoInteractions(mailer);
        verify(mailer, never()).passwordChanged(anyString(), any());
        verify(mailer, never()).accountDeleted(anyString(), anyLong());
    }
}
