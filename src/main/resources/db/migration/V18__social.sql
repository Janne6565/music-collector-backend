-- Friends: a handle to be found by, mutual friendship, and three separate answers about
-- who may see what.
--
-- Everything here is server-only. The collection itself stays local-first and syncs, but a
-- shelf somebody else can look at has to be answered by the server -- the viewer's device
-- has no copy of the owner's library and must never be handed one it is not allowed to see.

-- The handle is the only public identifier. The display name stays what it always was:
-- what the app calls you, never a way to find you.
ALTER TABLE users ADD COLUMN handle TEXT;

-- Case-insensitive, like the e-mail above it. Nobody should be able to claim @Anna next to
-- an existing @anna and collect the requests meant for them.
CREATE UNIQUE INDEX users_handle_lower_key ON users (lower(handle));

-- Every handle this account has ever held. Two purposes: enforcing the twice-a-year limit
-- without trusting a counter that a later feature might reset, and keeping a released
-- handle out of circulation for a while so the next claimant cannot inherit a stranger's
-- inbound requests.
CREATE TABLE handle_changes (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    handle     TEXT        NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX handle_changes_user_idx ON handle_changes (user_id, changed_at DESC);
CREATE INDEX handle_changes_handle_idx ON handle_changes (lower(handle), changed_at DESC);

-- Collection, wishlist and prices are three questions, not one setting. A public wishlist
-- over a friends-only shelf is the normal case, not an exotic one, so the two lists carry
-- separate columns rather than sharing a single "profile visibility".
--
-- The CHECK constraints are written out here on purpose. Under ddl-auto:update Hibernate
-- infers a constraint from the enum's current values and never revisits it, so adding a
-- fourth visibility later would break every insert until someone found the stale check.
CREATE TABLE sharing_settings (
    user_id               UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    collection_visibility TEXT        NOT NULL DEFAULT 'FRIENDS'
                                      CHECK (collection_visibility IN ('ONLY_ME', 'FRIENDS', 'PUBLIC')),
    wishlist_visibility   TEXT        NOT NULL DEFAULT 'FRIENDS'
                                      CHECK (wishlist_visibility IN ('ONLY_ME', 'FRIENDS', 'PUBLIC')),
    -- What you paid is off by default even on a public shelf. Someone sharing a collection
    -- is not thereby sharing what it cost them.
    prices_public         BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Listed, not private: off keeps you out of search results while a direct link to your
    -- handle still resolves under the visibility columns above.
    findable              BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Friendship is mutual and therefore one row, not two. Storing a row per direction would
-- make "are these two friends" a question with two answers that can disagree.
CREATE TABLE friendships (
    id           UUID        PRIMARY KEY,
    requester_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       TEXT        NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT friendships_not_self CHECK (requester_id <> addressee_id)
);

-- Unordered uniqueness: A asking B and B asking A are the same pending friendship, and the
-- second request must collide with the first rather than create a duplicate that both
-- sides can accept.
CREATE UNIQUE INDEX friendships_pair_key
    ON friendships (least(requester_id, addressee_id), greatest(requester_id, addressee_id));

CREATE INDEX friendships_requester_idx ON friendships (requester_id, status);
CREATE INDEX friendships_addressee_idx ON friendships (addressee_id, status);

-- One copy withheld, whatever the settings above say. A record can be embarrassing, a gift
-- not yet given, or simply nobody's business, and that is a per-copy decision rather than a
-- reason to close the whole shelf.
--
-- A mergeable field like every other column on a copy: hiding one on the phone has to reach
-- the laptop, so it carries its own clock and travels through sync.
ALTER TABLE copies ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
