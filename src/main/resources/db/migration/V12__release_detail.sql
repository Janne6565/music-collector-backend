-- What the pressing table on screen 10d shows beyond the label/catalog/country line.
--
-- All three arrive in the same MusicBrainz search response we already parse, so this is
-- storage catching up with data we were discarding, not a new upstream call.
--
-- `year` stays: it is what every list sorts and groups by, and deriving it from a partial
-- date ("1970", "1970-03", "1970-03-30" all occur) at every read would be wasteful.

ALTER TABLE releases ADD COLUMN release_date TEXT;
ALTER TABLE releases ADD COLUMN track_count  INTEGER;
ALTER TABLE releases ADD COLUMN disc_count   INTEGER;
