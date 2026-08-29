-- What the profile picture costs, so that "how much of your allowance is gone" can be
-- answered with one query rather than a walk through object storage.
--
-- The sleeve photos already carry their own size in photos.byte_size; the avatar is the
-- only stored object with no row of its own to hold it. Rendered size, not uploaded size:
-- an account is charged for what the bucket actually holds, which is the single 512px
-- JPEG the server wrote -- around fifty kilobytes whatever arrived.
ALTER TABLE users ADD COLUMN avatar_bytes BIGINT;

-- Left NULL for the handful of pictures that predate this column, and counted as nothing
-- until the picture is next replaced. Fifty kilobytes is a quarter of one percent of the
-- allowance, so the miscount can never be the reason an upload is refused, and guessing a
-- number here would be a fiction the sum could not tell from a measurement.
