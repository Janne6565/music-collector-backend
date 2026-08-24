-- Accounts exist only to enable sync. The app is fully usable with no row in this table:
-- an anonymous client keeps its whole collection in its own local store.
CREATE TABLE users (
    id               UUID        PRIMARY KEY,
    email            TEXT        NOT NULL,
    password_hash    TEXT        NOT NULL,
    -- Bumped to revoke every outstanding refresh token for this user at once.
    token_version    INTEGER     NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness: e-mail is the login identifier.
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));
