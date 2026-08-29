-- The profile picture (turn 27). One per account, replaced rather than versioned: the app
-- offers it in a single row on Account and there is no gallery behind it.
--
-- Account data rather than collection data, which is why it lives here and not in the
-- synced tables: it is the same picture on every device, it exists only for accounts, and
-- it is public wherever the handle resolves -- including on a shelf that is locked.

-- The object key in MinIO, or NULL for the overwhelming majority who never set one. The
-- key is derived (avatars/<user id>) rather than random, but storing it keeps the deletion
-- path honest: what we remove is what we wrote.
ALTER TABLE users ADD COLUMN avatar_key TEXT;

-- When the current picture landed. It is the cache-buster in the public URL -- the object
-- key never changes on replace, so without this a viewer holding yesterday's bytes would
-- keep them for as long as the cache header says.
ALTER TABLE users ADD COLUMN avatar_updated_at TIMESTAMPTZ;
