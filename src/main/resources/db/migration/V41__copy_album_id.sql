-- A copy records the album; the pressing becomes optional.
--
-- This is the shape wishlist_items has had since V5: album_id for the record, release_id
-- for which pressing of it, null when nobody picked one. A copy is the same question with
-- the same optional second half, so the two tables now agree.
--
-- It also stops a guess being stored as an answer. The add sheet seeds its pick from
-- whichever pressing the catalogue ranked first, so shelves record pressings their owners
-- never chose, indistinguishable from the ones they did.

ALTER TABLE copies ADD COLUMN album_id TEXT;

-- Backfill from the mirror: every existing copy points at a pressing, and a pressing knows
-- its album. Copies whose release was never mirrored here keep a null album_id, which the
-- clients already handle -- albumIdOf falls back to the release, exactly as before.
UPDATE copies
SET album_id = release_groups.external_id
FROM releases
    JOIN release_groups ON release_groups.id = releases.release_group_id
WHERE releases.external_id = copies.release_id
  AND copies.album_id IS NULL;

-- A hand-entered copy is in no catalogue and never will be, so its album is its own id for
-- the same reason its release already is: any device holding the copy can resolve both
-- without a cache row, and every filter that already skips `local:` keeps working.
UPDATE copies
SET album_id = release_id
WHERE album_id IS NULL
  AND release_id LIKE 'local:%';

-- Nullable, because "no pressing chosen" is now a legitimate state. Nothing writes null
-- yet: the clients that can are released after this.
ALTER TABLE copies ALTER COLUMN release_id DROP NOT NULL;

-- The shelf groups copies by album, and friends' shelves resolve them in a batch.
CREATE INDEX idx_copies_album_id ON copies (album_id) WHERE album_id IS NOT NULL;
