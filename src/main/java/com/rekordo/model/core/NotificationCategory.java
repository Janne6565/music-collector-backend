package com.rekordo.model.core;

/**
 * What may reach somebody outside the app (design 22a).
 *
 * <p>Four of them, and the list is deliberately short: every category is a promise about
 * how often it will speak, and one nobody can describe in a sentence should not exist.
 *
 * <p>The defaults are part of the design rather than a convention. A friend request is the
 * one thing worth interrupting for, so it arrives on both channels. The weekly digest is
 * mail by default and push only for people who do not read mail — board 22c killed the
 * per-record push outright, because "Anna added 7 copies" asks for nothing and lands at
 * whatever hour Anna shops. Product news is off until somebody asks for it.
 *
 * @param mailByDefault whether it goes out by mail on a fresh account
 * @param pushByDefault whether it buzzes on a fresh account
 * @param mailLocked    a notice you can silence is not a notice: security mail cannot be
 *                      switched off. The lock covers mail only — whether it also buzzes is
 *                      the account holder's to set.
 */
public enum NotificationCategory {
    FRIEND_REQUEST(true, true, false),
    FRIEND_ACTIVITY(true, false, false),
    SECURITY(true, true, true),
    PRODUCT_NEWS(false, false, false);

    private final boolean mailByDefault;
    private final boolean pushByDefault;
    private final boolean mailLocked;

    NotificationCategory(boolean mailByDefault, boolean pushByDefault, boolean mailLocked) {
        this.mailByDefault = mailByDefault;
        this.pushByDefault = pushByDefault;
        this.mailLocked = mailLocked;
    }

    public boolean mailByDefault() {
        return mailByDefault;
    }

    public boolean pushByDefault() {
        return pushByDefault;
    }

    public boolean mailLocked() {
        return mailLocked;
    }
}
