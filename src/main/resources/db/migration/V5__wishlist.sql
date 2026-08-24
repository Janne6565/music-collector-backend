-- Wishlist entries. User-owned and synced, exactly like copies.
--
-- A wish points at a release *group* (the album) and a desired format, not at a specific
-- pressing: "Ege Bamyasi on vinyl, ideally an original Spoon press" is a wish, and the
-- pressing only becomes a fact once you actually own a copy.

CREATE TABLE wishlist_items (
    id                  UUID        PRIMARY KEY,
    user_id             UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    release_group_mbid  TEXT        NOT NULL,
    -- Denormalised from the metadata mirror so the wishlist renders with no extra lookup
    -- and keeps working when the release group has not been cached on this device.
    title               TEXT        NOT NULL,
    artist_name         TEXT        NOT NULL,
    year                INTEGER,
    desired_format      TEXT,
    note                TEXT,
    created_at          BIGINT      NOT NULL,
    deleted_at          BIGINT,
    field_clocks        TEXT        NOT NULL,
    sync_seq            BIGINT      NOT NULL
);

CREATE INDEX wishlist_user_seq_idx ON wishlist_items (user_id, sync_seq);
