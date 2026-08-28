-- The format a wish was for, carried on its feed line.
--
-- A WISH_ADDED event stores the album id, and an album has no format: it is the wish that
-- wants one. Without this the feed could say "is looking for X" but not draw the thing
-- being looked for, so every wish line fell back to the no-format placeholder.
--
-- A plain varchar rather than an enum column, exactly as wishlist_items stores it: the
-- wanted format is a subset of the copy formats plus "any", which is null here.
ALTER TABLE activity_events ADD COLUMN wanted_format VARCHAR(16);

-- Backfilled from the wishes themselves, which still hold the answer: a feed line's
-- subject is the wish's id. Without this every line already on somebody's feed would stay
-- formatless forever, since nothing re-records a wish that was added months ago.
UPDATE activity_events e
SET wanted_format = w.desired_format
FROM wishlist_items w
WHERE e.type = 'WISH_ADDED'
  AND e.subject_id = w.id
  AND w.desired_format IS NOT NULL;
