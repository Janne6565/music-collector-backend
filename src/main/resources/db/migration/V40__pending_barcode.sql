-- A scan that could not be identified yet keeps its digits here until some device with a
-- connection can look them up. Nullable and unindexed on purpose: it is null on every row
-- that ever mattered, and the only query that reads it ("what is still waiting?") runs on
-- the client against its own small local store, never here.
ALTER TABLE copies ADD COLUMN pending_barcode VARCHAR(32);
ALTER TABLE wishlist_items ADD COLUMN pending_barcode VARCHAR(32);
