-- A display name, which screens 1f and 1l show ("Jonas Meyer") and the register screens
-- ask for. Until now the apps showed the e-mail address in its place.
--
-- Nullable: every existing account was created before there was anywhere to type one, and
-- inventing a name from the e-mail would put a guess in front of the person as fact.
ALTER TABLE users ADD COLUMN display_name TEXT;
