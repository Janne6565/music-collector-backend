-- A copy of a pressing no catalogue has: a bootleg, a test press, a tape somebody made.
--
-- Its release facts live on the copy rather than in the `releases` mirror because they are
-- the user's data, not the archive's -- `releases` is a shared cache keyed by MusicBrainz
-- and Discogs ids, and a row nobody else can see does not belong in it. The copy's
-- release_id is 'local:<the copy's own id>', so any device holding the copy can resolve
-- the pressing without a cache row at all.
--
-- Six columns rather than one JSON blob, so each merges under its own clock: correcting
-- the year on the phone and the label on the laptop has to keep both corrections, which
-- is the point of the field-level merge.

ALTER TABLE copies ADD COLUMN manual_title TEXT;
ALTER TABLE copies ADD COLUMN manual_artist TEXT;
ALTER TABLE copies ADD COLUMN manual_year INTEGER;
ALTER TABLE copies ADD COLUMN manual_label TEXT;
ALTER TABLE copies ADD COLUMN manual_catalog_number TEXT;
ALTER TABLE copies ADD COLUMN manual_format TEXT;
