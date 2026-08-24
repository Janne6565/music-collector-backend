-- Signing in with an external provider.
--
-- The provider only replaces the first step: once the identity is resolved the app issues
-- its own JWT pair, exactly as a password login does. Nothing downstream knows or cares
-- how someone signed in.

-- An account created purely through a provider has no password to hash.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE oauth_identities (
    id               UUID        PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         TEXT        NOT NULL,
    -- The provider's own stable id for the person. Not the e-mail: people change those,
    -- and some providers hand out per-app relay addresses that change too.
    provider_subject TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_subject)
);

CREATE INDEX oauth_identities_user_idx ON oauth_identities (user_id);

-- One-time CSRF state for the authorization-code flow. Stored rather than signed so it can
-- be consumed exactly once: a replayed callback must not be able to mint a second session.
CREATE TABLE oauth_states (
    state      TEXT        PRIMARY KEY,
    provider   TEXT        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
