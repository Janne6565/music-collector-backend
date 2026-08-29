-- Discogs' own bookkeeping, scrubbed out of names that are already stored.
--
-- Two markers leak in through the Discogs search path, which stores the artist half of
-- Discogs' combined "Artist - Title" string verbatim:
--
--   "Ben Howard (2)"  the *second* Ben Howard in Discogs' database. A disambiguation key,
--                     not a count -- and meaningless outside their catalogue.
--   "久石譲*"          the credit printed on this release differs from the artist's
--                     canonical entry. A fact about Discogs' data, not about the record.
--
-- `DiscogsMapper.artistOf` strips both from here on; this catches what is already written.
-- MusicBrainz-sourced rows are untouched by construction: neither marker exists there, and
-- the pattern is anchored and digits-only, so a name that genuinely ends in brackets
-- ("Sunn O)))", "The The (Band)") survives. Track credits are left alone entirely -- they
-- come only from MusicBrainz.
--
-- Every UPDATE below guards on `<> ''`: a row whose name is *nothing but* a marker keeps
-- what it has. A puzzling artist beats a blank one.

-- The shared metadata mirror. Read by every user, so this is the one that fixes the
-- library for anyone who has not cached the release yet.
UPDATE release_groups
SET artist_name = btrim(regexp_replace(regexp_replace(artist_name, '\*$', ''), '\s*\(\d+\)$', ''))
WHERE artist_name ~ '(\*|\s*\(\d+\))$'
  AND btrim(regexp_replace(regexp_replace(artist_name, '\*$', ''), '\s*\(\d+\)$', '')) <> '';

UPDATE releases
SET artist_name = btrim(regexp_replace(regexp_replace(artist_name, '\*$', ''), '\s*\(\d+\)$', ''))
WHERE artist_name ~ '(\*|\s*\(\d+\))$'
  AND btrim(regexp_replace(regexp_replace(artist_name, '\*$', ''), '\s*\(\d+\)$', '')) <> '';

-- Feed lines carry their own copy on purpose (the mirror row behind them may be evicted),
-- so they have to be rewritten separately. Nothing syncs these -- they are read server-side.
UPDATE activity_events
SET artist_name = btrim(regexp_replace(regexp_replace(artist_name, '\*$', ''), '\s*\(\d+\)$', ''))
WHERE artist_name IS NOT NULL
  AND artist_name ~ '(\*|\s*\(\d+\))$'
  AND btrim(regexp_replace(regexp_replace(artist_name, '\*$', ''), '\s*\(\d+\)$', '')) <> '';

-- The wishlist is synced user data, and that makes it the delicate one.
--
-- Rewriting the column alone would be silently undone: sync merges per field, last write
-- wins by HLC stamp, so a device still holding "Ben Howard (2)" under a *newer* clock would
-- push its version back on the next sync and the migration would look like it never ran.
-- So the row's `artistName` clock is set to a fresh stamp, and `sync_seq` is drawn from the
-- same sequence the push path uses, so devices actually pull the change rather than sitting
-- on a cursor that never moves.
--
-- The stamp is an HLC in the client's own encoding -- 15-digit wall clock, 4-hex counter,
-- node id -- which compares lexicographically exactly as `hlcCompare` does. The node is
-- named for this migration so a stamp nothing on any device produced is identifiable in a
-- field-clock map later.
UPDATE wishlist_items
SET artist_name = btrim(regexp_replace(regexp_replace(artist_name, '\*$', ''), '\s*\(\d+\)$', '')),
    field_clocks = (
        COALESCE(NULLIF(field_clocks, '')::jsonb, '{}'::jsonb)
        || jsonb_build_object(
            'artistName',
            lpad(((extract(epoch FROM now()) * 1000)::bigint)::text, 15, '0') || ':0000:server-v35')
    )::text,
    sync_seq = nextval('copies_sync_seq')
WHERE artist_name ~ '(\*|\s*\(\d+\))$'
  AND btrim(regexp_replace(regexp_replace(artist_name, '\*$', ''), '\s*\(\d+\)$', '')) <> '';
