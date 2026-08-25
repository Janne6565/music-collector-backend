-- Which picture stands for a copy is otherwise the order of its photos: starring one moves
-- it to the front, and the front one is what the library grid and the detail hero draw.
--
-- The catalogue's own artwork cannot be said that way. It is not a photo row, it belongs to
-- the release rather than to the copy, and so it has no position to be moved to -- which
-- left it un-choosable the moment a copy had a single photo of its own. This flag is the
-- one preview choice the order cannot represent, and only that one.
--
-- Copies recorded before this migration keep today's behaviour: their own photos win, and
-- the catalogue cover stands in only where there are none.

ALTER TABLE copies ADD COLUMN prefer_catalog_art BOOLEAN NOT NULL DEFAULT FALSE;
