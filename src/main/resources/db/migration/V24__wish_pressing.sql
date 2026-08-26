-- The pressing a wishlist entry was made from (design turn 19).
--
-- A wish still points at a release *group*: that is what "one entry per release" is
-- checked against, and what the covers endpoint is asked about. But the search a wish is
-- made from returns pressings, and each pressing has its own sleeve -- so an entry that
-- remembered only the album had its cover re-resolved album-level on every read and came
-- back as whichever pressing the mirror ranks first, rather than the one that was on
-- screen when the entry was made.
--
-- Nullable, and no foreign key to the mirror, for the same reason `album_id` has none: a
-- client may name a pressing this deployment has never cached, and that is a fact to
-- store and reconcile rather than reject. Null means "no pressing was picked" -- a
-- hand-typed entry, or one made before this column existed -- and reading falls back to
-- the album exactly as it did before.

ALTER TABLE wishlist_items ADD COLUMN release_id TEXT;
