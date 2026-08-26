-- Where a push could arrive (design 22a, second list).
--
-- Two levels, deliberately: *what* may reach you is the account's and lives in
-- notification_preferences; *which device* buzzes is the device's and lives here. A phone in
-- a drawer and a phone in a pocket disagree, and one mute per device is the whole of that
-- disagreement -- the categories are never duplicated per phone.
CREATE TABLE notification_devices (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- The client's own stable id for itself, so re-registering updates rather than piles up.
    device_id    TEXT        NOT NULL,
    -- An Expo push token (ExponentPushToken[...]). Not a secret in the sense a password is,
    -- but it addresses somebody's phone, so it goes out of here in nothing but a send.
    push_token   TEXT        NOT NULL,
    platform     TEXT        NOT NULL,
    -- What the person recognises it by in the list: "iPhone", "iPad".
    label        TEXT,
    -- Set while this device is muted. A timestamp rather than a boolean because the screen
    -- says "muted here since June", and a boolean cannot answer that.
    muted_at     TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, device_id)
);

CREATE INDEX notification_devices_user_idx ON notification_devices (user_id);
