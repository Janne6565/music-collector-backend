package com.rekordo.model.core;

/**
 * What this account's pictures cost it, and what it is allowed.
 *
 * <p>Split rather than summed to one number, because the two halves behave differently: the
 * photos are collection data the person can delete one by one, the profile picture is a
 * single object they can only replace. A screen that showed one total could say "you are
 * full" without being able to say what would help.
 *
 * @param photoBytes  the sleeve photos this account has not deleted
 * @param photoCount  how many of them there are, so a bar can say "34 photos" rather than a
 *                    number of megabytes nobody can picture
 * @param avatarBytes the rendered profile picture, or 0 for an account with none
 * @param usedBytes   the two above, added up -- computed here so that every client agrees
 *                    with the number the server refuses uploads by
 * @param quotaBytes  the allowance, which is configuration rather than a constant
 */
public record StorageUsageDto(
        long photoBytes, long photoCount, long avatarBytes, long usedBytes, long quotaBytes) {}
