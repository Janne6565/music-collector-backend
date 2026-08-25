-- Where a wishlist entry sits once the list has been hand-sorted (design turn 16).
--
-- The order a person drags a wishlist into is data, not a device preference: dragging
-- "closest to finding" to the top is a statement about the list, and an order that only
-- existed on the phone would be gone the moment they opened the web app. So it is an
-- ordinary mergeable field, stamped and reconciled like every other one.
--
-- NULL means "never placed by hand", which is not the same as position 0 -- an entry added
-- since the last drag sorts after the placed ones rather than jumping to the top of an
-- order it was never part of.

ALTER TABLE wishlist_items ADD COLUMN sort_index INTEGER;
