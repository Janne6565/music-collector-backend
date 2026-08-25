-- A stored release id now says which catalogue it came from.
--
-- The app reads two: MusicBrainz for artists, Discogs for the physical pressings that
-- MusicBrainz frequently does not have. They share no identifiers, so an id on its own is
-- ambiguous.
--
-- Source and id travel as ONE value ("musicbrainz:<uuid>", "discogs:31679120") rather than
-- two columns. A copy's release reference is a field-level mergeable value: as two fields
-- each would merge under its own clock, and one device's MUSICBRAINZ could combine with
-- another's Discogs integer to produce a copy pointing at nothing. One field makes that
-- unrepresentable.
--
-- Nothing is dropped. Every id written before today came from MusicBrainz, so prefixing is
-- exactly correct and the conversion is lossless.

ALTER TABLE releases       RENAME COLUMN mbid TO external_id;
ALTER TABLE release_groups RENAME COLUMN mbid TO external_id;

ALTER TABLE releases       ALTER COLUMN external_id TYPE TEXT USING 'musicbrainz:' || external_id;
ALTER TABLE release_groups ALTER COLUMN external_id TYPE TEXT USING 'musicbrainz:' || external_id;

-- Renaming a column leaves its UNIQUE constraint under the old name, which reads as a
-- mystery to whoever meets it next.
ALTER TABLE releases       RENAME CONSTRAINT releases_mbid_key       TO releases_external_id_key;
ALTER TABLE release_groups RENAME CONSTRAINT release_groups_mbid_key TO release_groups_external_id_key;

ALTER TABLE copies         RENAME COLUMN release_mbid TO release_id;
UPDATE      copies         SET release_id = 'musicbrainz:' || release_id
                           WHERE release_id NOT LIKE '%:%';

ALTER TABLE wishlist_items RENAME COLUMN release_group_mbid TO album_id;
UPDATE      wishlist_items SET album_id = 'musicbrainz:' || album_id
                           WHERE album_id NOT LIKE '%:%';

-- The field-level clocks are keyed by field name, and two of those names just changed.
-- A clock left under the old key would read as "never set", losing every edit that field
-- has ever won.
UPDATE copies
   SET field_clocks = replace(field_clocks, '"releaseMbid"', '"releaseId"')
 WHERE field_clocks LIKE '%"releaseMbid"%';

UPDATE wishlist_items
   SET field_clocks = replace(field_clocks, '"releaseGroupMbid"', '"albumId"')
 WHERE field_clocks LIKE '%"releaseGroupMbid"%';
