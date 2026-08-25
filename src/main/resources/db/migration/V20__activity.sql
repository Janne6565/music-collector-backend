-- The Friends feed.
--
-- Written on the way through sync rather than derived from the copies themselves. Two
-- reasons: a copy carries no record of *why* it appeared, and a feed derived from rows
-- would replay a whole collection the moment somebody signed in on a new device.
--
-- What is never written here is as much of the design as what is: an import of two hundred
-- records is silent, and so is the batch that lands the first time an account signs in.
-- Only a copy somebody added by hand, one at a time, is worth telling their friends about.
CREATE TABLE activity_events (
    id          UUID        PRIMARY KEY,
    actor_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        TEXT        NOT NULL CHECK (type IN (
                                'COPY_ADDED', 'WISH_ADDED', 'WISH_FULFILLED', 'FRIENDSHIP_ACCEPTED')),
    -- The copy, the wishlist item, or the other person -- whichever the type is about.
    subject_id  UUID,
    release_id  TEXT,
    -- Denormalised on purpose. The mirror row behind a release is a cache that any client
    -- may drop and the cleaner may evict, and a feed line that loses its title six months
    -- later is worse than one that cannot be re-resolved.
    title       TEXT,
    artist_name TEXT,
    -- The device's own UTC, trusted -- and clamped on the way in so it can never be in the
    -- future. A clock set to next year would otherwise pin one line to the top of every
    -- friend's feed permanently.
    occurred_at TIMESTAMPTZ NOT NULL,
    -- When the server heard about it. Kept beside the above so a suspicious gap between
    -- them is still visible after the fact.
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX activity_events_actor_idx ON activity_events (actor_id, occurred_at DESC);

-- One line per record, ever. A copy that is edited, deleted and pushed again does not
-- announce itself a second time.
CREATE UNIQUE INDEX activity_events_subject_key ON activity_events (actor_id, type, subject_id);
