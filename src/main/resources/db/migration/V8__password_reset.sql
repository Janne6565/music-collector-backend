-- One-time password reset tokens.
--
-- Only a hash is stored: the raw token goes out in the e-mail and nowhere else, so a
-- database leak cannot be turned into account takeovers.
CREATE TABLE password_resets (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    -- Set when redeemed, so a link works exactly once even if it is still in date.
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX password_resets_user_idx ON password_resets (user_id);
