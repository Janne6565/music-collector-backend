-- V15 could say "show the catalogue's artwork instead of my photos" but had no way to say
-- "this is not one of my images at all" -- and the art the archive holds for a pressing is
-- sometimes the wrong cover, or simply one you would rather not have on your shelf.
--
-- A second boolean beside it would have let a copy prefer and hide the same picture at
-- once. AUTO, PREFERRED and HIDDEN are answers to one question, so they are one column.
--
-- V15 shipped minutes before this and is edited by replacement rather than in place, since
-- it has already been applied wherever staging runs. Existing rows carry their answer
-- across: TRUE was PREFERRED, and everything else has said nothing yet.

ALTER TABLE copies ADD COLUMN catalog_art TEXT NOT NULL DEFAULT 'AUTO';

UPDATE copies SET catalog_art = 'PREFERRED' WHERE prefer_catalog_art;

ALTER TABLE copies DROP COLUMN prefer_catalog_art;
