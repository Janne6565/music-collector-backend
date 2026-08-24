-- A shared, cached mirror of MusicBrainz. These rows are not user data: the same release
-- serves every user, and an anonymous client reads them through the open metadata proxy.
--
-- Three levels, matching how MusicBrainz models music and how screens 1c/1g/2a use it:
--   release_group  the album           Bitches Brew
--   release        a specific edition  Columbia GP 26, 2xLP, US, 1970
--   (copy)         the user's item     lives in the client's local store, not here

CREATE TABLE release_groups (
    id                  UUID        PRIMARY KEY,
    mbid                UUID        NOT NULL UNIQUE,
    title               TEXT        NOT NULL,
    artist_name         TEXT        NOT NULL,
    artist_mbid         UUID,
    first_release_year  INTEGER,
    fetched_at          TIMESTAMPTZ NOT NULL
);

CREATE TABLE releases (
    id                UUID        PRIMARY KEY,
    mbid              UUID        NOT NULL UNIQUE,
    release_group_id  UUID        NOT NULL REFERENCES release_groups (id),
    title             TEXT        NOT NULL,
    artist_name       TEXT        NOT NULL,
    -- VINYL | CD | CASSETTE | DIGITAL | OTHER. Deliberately TEXT with no CHECK
    -- constraint: adding a format later must not require a data migration.
    format            TEXT        NOT NULL,
    year              INTEGER,
    label             TEXT,
    catalog_number    TEXT,
    country           TEXT,
    barcode           TEXT,
    cover_art_url     TEXT,
    -- Sampled from the cover once, at import. `luminance` is WCAG relative luminance;
    -- the clients compare it against their dark-chrome threshold.
    dominant_color    TEXT,
    accent_color      TEXT,
    luminance         DOUBLE PRECISION,
    fetched_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX releases_release_group_idx ON releases (release_group_id);
-- Barcode scanning (screen 1e) looks up by EAN before ever calling MusicBrainz.
CREATE INDEX releases_barcode_idx ON releases (barcode) WHERE barcode IS NOT NULL;
