-- Moving an account to a different address (design 21g).
--
-- The rule the whole flow is shaped by: the old address keeps working -- signing in, resets,
-- everything -- until the new one answers, so a typo cannot lock anybody out. The account is
-- not un-confirmed while it waits; it is confirmed at the old address and pending at the new.
--
-- That rides on `email_verifications` rather than a table of its own, because a change *is* a
-- confirmation with somewhere else to put the answer. A row with `new_email` set moves the
-- account when redeemed; a row without it confirms the address already on file.
ALTER TABLE email_verifications
    -- The address being moved to. NULL means this row confirms the address on the account.
    ADD COLUMN new_email        TEXT,
    -- Kept so a cancellation can put back exactly what was there, including its case.
    ADD COLUMN previous_email   TEXT,
    -- The old mailbox's undo. It is the only defence if somebody else is at the keyboard,
    -- which is why it outlives the change itself by a day rather than expiring with the link.
    ADD COLUMN cancel_token_hash TEXT,
    ADD COLUMN cancel_expires_at TIMESTAMPTZ;

CREATE UNIQUE INDEX email_verifications_cancel_token_idx
    ON email_verifications (cancel_token_hash)
    WHERE cancel_token_hash IS NOT NULL;
