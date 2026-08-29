-- A photo's storage key and content type used to be whatever a sync push said they were.
-- The key decides whose object is served and deleted, and the content type decides what a
-- browser does with the bytes -- which are served from the same origin as the web app. The
-- server owns both from now on; this is the same correction applied backwards, to any row
-- written before it did.
--
-- A no-op for every legitimately written row: the upload endpoint is the only thing that has
-- ever minted a key, and it has always minted exactly this one. So no field clock is stamped
-- and no sync_seq bumped: the only rows this can touch are ones no honest device is holding,
-- and the push path now overrides these three fields whatever clock arrives with them.
UPDATE photos
SET storage_key = user_id::text || '/' || id::text
WHERE storage_key IS NOT NULL
  AND storage_key <> user_id::text || '/' || id::text;

-- Stored lowercased, because that is how it is now compared against the allowlist.
UPDATE photos
SET content_type = lower(content_type)
WHERE content_type <> lower(content_type);

UPDATE photos
SET content_type = 'application/octet-stream'
WHERE content_type NOT IN ('image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif');
