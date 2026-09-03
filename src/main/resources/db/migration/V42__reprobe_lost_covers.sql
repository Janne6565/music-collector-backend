-- Reopens the cover question for every release that was told it has none.
--
-- The palette sampler wrote `has_cover_art = false` whenever the fetch came back empty, and
-- a fetch that timed out or was throttled came back empty too. A false there is served as a
-- null coverArtUrl, and every client that refreshes its catalogue cache then drops the
-- picture as well -- so one bad minute took the artwork off a record permanently, on the
-- server and on every device. Reported from the field: a shelf lost a record's cover during
-- an evening of scanning, and nothing brought it back.
--
-- The code no longer records an unreachable probe as an answer. This clears the answers it
-- already recorded, but only where there is an address to ask again: a row with no URL at
-- all has nothing to re-probe and its false is the truth. The palette goes with it, because
-- a palette is what makes `getRelease` skip the probe.
--
-- Releases that really have no cover cost one probe each and are marked false again.

UPDATE releases
SET has_cover_art = NULL,
    dominant_color = NULL,
    accent_color = NULL,
    lightness = NULL
WHERE has_cover_art = FALSE
  AND cover_art_url IS NOT NULL;
