-- User-owned copies. Unlike release_groups/releases (a shared cache of MusicBrainz), these
-- are the user's own records and the only rows that sync.
--
-- Ids are client-generated: a copy created offline, with no account, must keep its identity
-- when it later syncs, so the server cannot be the one to name it.

CREATE SEQUENCE copies_sync_seq;

CREATE TABLE copies (
    id                UUID        PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    release_mbid      TEXT        NOT NULL,
    condition         TEXT,
    price_paid_cents  INTEGER,
    currency          TEXT        NOT NULL,
    -- ISO date, no time: you know the day you bought a record, not the minute.
    purchased_on      TEXT,
    purchased_at      TEXT,
    notes             TEXT,
    -- The other version of the notes, derived by the merge rather than written by a device.
    notes_conflict    TEXT,
    rating            INTEGER,
    -- Epoch milliseconds, matching the clients exactly. The merge is a shared contract, so
    -- a representation change here without the matching change there would break it.
    created_at        BIGINT      NOT NULL,
    -- Tombstone. A deleted row that vanished would be handed straight back on next sync.
    deleted_at        BIGINT,
    -- Encoded HLC per mergeable field. Never queried into, so plain text rather than JSONB.
    field_clocks      TEXT        NOT NULL,
    -- Monotonic per write. Pull asks for everything above the client's cursor, which is
    -- immune to the clock skew a timestamp cursor would suffer from.
    sync_seq          BIGINT      NOT NULL
);

CREATE INDEX copies_user_seq_idx ON copies (user_id, sync_seq);
