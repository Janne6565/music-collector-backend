-- What may reach somebody outside the app (design 22a).
--
-- One row per choice that differs from the default, not per category: the defaults are in
-- NotificationCategory, and storing them would mean a new category needs a backfill before
-- anybody's screen reads right. An account with no rows here is an account that has never
-- opened the screen, which is the common case and costs nothing.
--
-- The preferences follow the *account*, deliberately unlike everything on Settings, which
-- stays on the device that set it. Which device may buzz is a separate, shorter question --
-- and it is not answered here, because there is no push transport yet and therefore no
-- device that could receive one. That table arrives with push.
CREATE TABLE notification_preferences (
    user_id  UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category TEXT    NOT NULL,
    mail     BOOLEAN NOT NULL,
    push     BOOLEAN NOT NULL,
    PRIMARY KEY (user_id, category)
);
