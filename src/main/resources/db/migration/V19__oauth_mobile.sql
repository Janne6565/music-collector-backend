-- External sign-in from the phone.
--
-- The web flow ends with a refresh cookie, which a native app cannot read: it keeps its
-- tokens in the platform keychain and sends them itself. So the callback has to know which
-- kind of client started the flow, and the app needs a way to pick the session up after
-- the browser hands control back.

-- Which client began this authorization. Recorded at the start, because the callback
-- arrives from the provider and carries nothing about who asked.
ALTER TABLE oauth_states ADD COLUMN client TEXT NOT NULL DEFAULT 'WEB';

-- The handoff from the browser back into the app.
--
-- The callback cannot simply redirect with a refresh token in the query string: a deep
-- link passes through the OS and lands in the browser's history. Instead it redirects with
-- a one-time code that is worthless on its own, and the app trades it -- over its own TLS
-- connection -- for the session. Only the hash is stored, as with password resets, so a
-- database leak is not a pile of pending sign-ins.
CREATE TABLE oauth_handoffs (
    code_hash  TEXT        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX oauth_handoffs_user_idx ON oauth_handoffs (user_id);
