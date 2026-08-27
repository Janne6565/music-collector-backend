-- The titles behind `releases.track_count` (design 26).
--
-- Part of the shared mirror, not user data: the same tracklist serves every account and an
-- anonymous visitor reads it through the open metadata proxy, exactly like the release row
-- it hangs off.
--
-- Persisted rather than fetched per sheet because MusicBrainz is paced at one request per
-- second for the whole process. A detail sheet that re-asked upstream every time it opened
-- would queue behind every other lookup in the app, and the tracklist is the slowest thing
-- on the sheet already.

CREATE TABLE release_tracks (
    id               UUID    PRIMARY KEY,
    release_id       UUID    NOT NULL REFERENCES releases (id) ON DELETE CASCADE,
    -- Which disc/LP of the release, 1-based, in catalogue order.
    medium_position  INTEGER NOT NULL,
    -- '12" Vinyl', 'CD', … as MusicBrainz words it. Null happens on sparse entries.
    medium_format    TEXT,
    -- Named discs occur on box sets only; MusicBrainz sends '' far more often than null,
    -- so both mean "unnamed" to every reader of this table.
    medium_title     TEXT,
    position         INTEGER NOT NULL,
    -- TEXT, and never an integer: vinyl is numbered 'A1', 'B2', and a 2-LP set's second
    -- medium starts at 'C1'. The catalogue's numbering is shown verbatim (design 26),
    -- so nothing here may be recomputed from `position`.
    number           TEXT    NOT NULL,
    title            TEXT    NOT NULL,
    -- Milliseconds, and frequently absent within an otherwise complete disc. The sheet
    -- leaves the cell empty rather than drawing a dash, so null must survive to the client.
    length_ms        INTEGER,
    -- The track's own credit. Stored even when it equals the release artist -- that
    -- comparison is a presentation decision (a compilation shows it, an album does not)
    -- and re-fetching the tracklist to change it would cost an upstream call.
    artist_name      TEXT
);

CREATE INDEX release_tracks_release_idx ON release_tracks (release_id, medium_position, position);

-- Distinguishes "never asked" from "asked, and this release genuinely has no tracks".
-- Without it an artless entry re-queries MusicBrainz on every single open, which is the
-- mistake `has_cover_art` was added to fix for covers.
ALTER TABLE releases ADD COLUMN tracks_fetched_at TIMESTAMPTZ;
