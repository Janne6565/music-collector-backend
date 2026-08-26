-- An album's own sleeve, for the albums no mirrored pressing can supply one for.
--
-- A wishlist entry names an album, and artwork belongs to a pressing, so the covers
-- endpoint answers by finding a pressing the mirror already holds. An album that reached
-- this deployment any other way -- an imported archive, a collection built elsewhere --
-- has no such pressing, and nothing ever asked a catalogue about it. A MusicBrainz album
-- could still be resolved, because the Cover Art Archive builds an address straight from
-- the group id; a Discogs one had nowhere to go at all.
--
-- cover_fetched_at is separate from fetched_at on purpose: a row created while adopting a
-- release has been fetched but never asked about its cover, and "asked, and there was
-- nothing" has to be tellable from "never asked" or every request re-asks forever.
ALTER TABLE release_groups
    ADD COLUMN cover_art_url    TEXT,
    ADD COLUMN cover_fetched_at TIMESTAMPTZ;
