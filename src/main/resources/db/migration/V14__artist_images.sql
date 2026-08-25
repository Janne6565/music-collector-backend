-- Portraits for the artist rows and the artist screen header (screens 10b/10c/10d).
--
-- MusicBrainz has no artist images and Discogs has no MusicBrainz ids, so finding one is
-- two upstream calls: MusicBrainz's own `discogs` URL relation gives the exact Discogs
-- artist, and that artist carries the pictures. Matching the two databases by *name*
-- would be cheaper and wrong — MusicBrainz holds at least three artists called
-- "Daughter", which is the whole reason a disambiguation line is drawn on every row.
--
-- Cached because that pair of calls is expensive and the answer never changes in a way
-- anybody would notice. MusicBrainz is paced at one request a second, so an uncached
-- artist costs about a second of somebody's wait; paying it once per artist, ever, is
-- what makes filling in a list of five of them tolerable.
--
-- Three states, like has_cover_art in V11:
--   no row              nobody has asked yet
--   row, image_url NULL asked, and there is genuinely no picture
--   row, image_url set  asked, and here it is
-- The middle case is an answer worth storing: most of its cost is the lookup that
-- discovered the artist has no Discogs link at all, and re-paying that on every render
-- of a row that will never have a portrait is the case worth avoiding.

CREATE TABLE artist_images (
    -- The MusicBrainz artist id, which is the only id the rest of the app knows an
    -- artist by. No surrogate key: there is exactly one row per artist and nothing
    -- references it.
    mbid              UUID        PRIMARY KEY,
    -- Discogs' 150px thumbnail. The avatars that use it are 46px and 62px, so the
    -- thumbnail is already generous at twice the pixel density, and the full-size image
    -- is frequently over half a megabyte.
    image_url         TEXT,
    -- Kept for support: when a portrait looks wrong, this is what says which Discogs
    -- artist we resolved to, without having to replay the lookup.
    discogs_artist_id BIGINT,
    fetched_at        TIMESTAMPTZ NOT NULL
);
