-- Whether the Cover Art Archive actually holds a front cover for this release.
--
-- The URL was always constructed from the mbid and handed to clients whether or not any
-- bytes existed behind it, so roughly four releases in ten pointed at a 404 and every
-- client had to discover that by failing to load an image.
--
-- Three states, deliberately: TRUE and FALSE are answers, NULL means "not asked yet".
-- A search result is persisted before anything has probed the archive, and claiming there
-- is no cover would be as wrong as claiming there is one.

ALTER TABLE releases ADD COLUMN has_cover_art BOOLEAN;
