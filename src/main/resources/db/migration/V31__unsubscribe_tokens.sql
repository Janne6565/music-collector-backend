-- The one-click way out of a category, from a mail client with no session (design 22f).
--
-- One row per (account, category) and reused, so every digest carries the same link and an
-- old mail keeps working. Only a hash is stored, the same as every other token here: the raw
-- value exists in the mail and nowhere else.
--
-- Deliberately per category. A link that also silenced security notices would be a trap,
-- which is why the token names exactly what it switches off and cannot name anything else.
CREATE TABLE notification_unsubscribe_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category   TEXT        NOT NULL,
    token_hash TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, category)
);
