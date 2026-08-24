-- Sleeve photos: the pictures you take of your own copy, as opposed to the catalogue
-- artwork that comes from the Cover Art Archive.
--
-- Only the metadata lives here. The bytes go to object storage, keyed by `storage_key`,
-- because a database is a poor place to keep multi-megabyte blobs and an even worse one
-- to stream them from.

CREATE TABLE photos (
    id            UUID        PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Not a foreign key to copies: a photo can arrive from a device that has already
    -- synced its picture but not yet the copy it belongs to, and rejecting it would lose
    -- the upload for good.
    copy_id       UUID        NOT NULL,
    storage_key   TEXT        NOT NULL,
    content_type  TEXT        NOT NULL,
    byte_size     BIGINT      NOT NULL,
    -- Position in the strip, so reordering is an ordinary field-level merge.
    sort_index    INTEGER     NOT NULL DEFAULT 0,
    created_at    BIGINT      NOT NULL,
    deleted_at    BIGINT,
    field_clocks  TEXT        NOT NULL,
    sync_seq      BIGINT      NOT NULL
);

CREATE INDEX photos_user_seq_idx ON photos (user_id, sync_seq);
CREATE INDEX photos_copy_idx ON photos (copy_id);
