-- A photo can now picture a wishlist entry instead of a copy (design turn 18).
--
-- A wish for a record no catalogue has can never be handed artwork by the metadata
-- mirror -- nobody has it -- so the only cover such an entry can ever have is one its
-- owner uploaded. Rather than a second table with a second set of bytes, the photo's
-- owner widens: exactly one of `copy_id` and `wish_id` is set.
--
-- Nothing else about a photo changes. The object key has always been `<user>/<photo>`
-- with no copy in it, and `copy_id` was already a mergeable field, so a photo has always
-- been able to change what it belongs to.
--
-- No CHECK constraint enforcing "exactly one owner", deliberately, and for the same
-- reason `copy_id` is not a foreign key: a row that arrives from a client this server has
-- not met yet should be stored and reconciled, not rejected. A photo that names no owner
-- is unreachable rather than dangerous, and it is the client's bug to fix.

ALTER TABLE photos ALTER COLUMN copy_id DROP NOT NULL;
ALTER TABLE photos ADD COLUMN wish_id UUID;

CREATE INDEX photos_wish_idx ON photos (wish_id);
