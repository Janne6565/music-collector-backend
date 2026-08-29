-- The state was single-use, unexpired and provider-matched, but it was not tied to anybody:
-- whoever presented it finished the sign-in. That is login CSRF -- an attacker starts a flow,
-- holds the callback URL, and gets somebody else's browser to load it, after which that
-- browser is signed into the attacker's account and every record it adds goes there.
--
-- So the authorize step now also sets a cookie carrying a secret this column holds the hash
-- of, and the callback is only completed by the browser that has it. Nullable, because rows
-- already in flight when this deploys have none; those are refused at the callback and the
-- person signs in again, which costs one retry inside the state's ten-minute life.
ALTER TABLE oauth_states ADD COLUMN binding_hash TEXT;
