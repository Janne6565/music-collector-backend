-- What was agreed to, in which version, and when (design turn 17).
--
-- Art. 7 Abs. 1 DSGVO puts the burden of proof on us: "they ticked a box" is not a record,
-- and neither is a boolean on the account. What is needed is the document and the version,
-- because a policy that has been rewritten twice since is not the one anybody accepted.
--
-- One row per statement rather than one per sign-up. The registration screen shows two
-- ticks -- the agreement and the age confirmation -- and the first of them covers two
-- documents, so a "consents" row count is not a tick count and was never meant to be.
--
-- It cascades with the account on purpose. The delete screen promises that everything goes,
-- and a proof-of-consent row still names the person it is about; keeping one to prove a
-- consent that no longer has a subject would make that promise false.
CREATE TABLE user_consents (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- TERMS | PRIVACY | AGE. Text rather than an enum type: a new statement should be an
    -- ordinary insert, not a migration that rewrites a type every client has to agree on.
    document    TEXT        NOT NULL,
    -- The version as the document itself carried it at the moment of acceptance. Stamped by
    -- the server from its own constants, never taken from the request: an old client must
    -- not be able to record consent to a document that has since been rewritten.
    version     TEXT        NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX user_consents_user_idx ON user_consents (user_id, accepted_at DESC);
