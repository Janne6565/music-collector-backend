-- A copy is graded twice, not once: sellers list media and sleeve separately, and a
-- near-mint record in a ring-worn jacket is a different object from a near-mint one in a
-- near-mint jacket.
--
-- The existing `condition` column keeps its name and becomes the media grade -- renaming it
-- would have meant a coordinated rename in the sync contract for no gain. Copies recorded
-- before this migration simply have no sleeve grade, which is the same as "not recorded".

ALTER TABLE copies ADD COLUMN sleeve_condition TEXT;
