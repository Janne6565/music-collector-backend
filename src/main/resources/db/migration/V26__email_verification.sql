-- Confirming that the address on an account is reachable by the person holding it.
--
-- Nothing is gated on it: the app is local-first and an account only adds sync, so refusing
-- to sync an unconfirmed address would punish the wrong thing. What it buys is that a
-- password reset and a security notice go somewhere the owner can actually read.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

-- Accounts made through a provider are confirmed by the provider, which is already what
-- OAuthUserResolver relies on when it links a provider identity to an existing address.
-- Placeholder addresses (Apple and Google may withhold one) are excluded: nothing was
-- confirmed about a mailbox that does not exist.
UPDATE users u
SET email_verified_at = u.created_at
WHERE u.email NOT LIKE '%@no-email.invalid'
  AND EXISTS (SELECT 1 FROM oauth_identities o WHERE o.user_id = u.id);

-- Same shape as password_resets (V8), and for the same reason: only a hash is stored, so a
-- database leak is not a pile of confirmed addresses.
CREATE TABLE email_verifications (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    -- Set when redeemed, so a link works exactly once even if it is still in date.
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX email_verifications_user_idx ON email_verifications (user_id);
