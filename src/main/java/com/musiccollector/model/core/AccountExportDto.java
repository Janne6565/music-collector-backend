package com.musiccollector.model.core;

import java.time.Instant;
import java.util.List;

/**
 * Everything the server holds about one account, in one file (design turn 17, screen 17g).
 *
 * <p>This is the Art. 15 answer and the Art. 20 answer at once, which is why it is JSON and
 * why it reuses the sync DTOs rather than inventing prettier ones: portability means the
 * file can be read back, and the shapes the app already speaks are the ones that can be.
 * The CSV export beside it is the human-readable half and holds copies only.
 *
 * <p>Photo <em>bytes</em> are not in here -- they are megabytes each and already downloadable
 * one by one -- but every photo's metadata is, including the URL its bytes live at.
 *
 * @param account     who the account is, as the server knows it
 * @param consents    what was agreed to and when, newest first
 * @param sharing     the visibility answers, so an export shows what was public at the time
 * @param friends     the other people this account is connected to, by handle
 * @param providers   which sign-in providers are linked, never their tokens
 */
public record AccountExportDto(
        Instant exportedAt,
        AccountDto account,
        List<ConsentDto> consents,
        SharingSettingsDto sharing,
        List<SyncCopyDto> copies,
        List<SyncWishDto> wishes,
        List<SyncPhotoDto> photos,
        List<FriendExportDto> friends,
        List<String> providers) {

    /**
     * The account itself. Deliberately not {@link UserDto}: an export may name things the
     * app has no screen for, and tying the two together would make one of them wrong later.
     */
    public record AccountDto(
            java.util.UUID id, String email, String displayName, String handle, Instant createdAt) {}

    /**
     * One connection. The other person is named by handle only -- the handle is the thing
     * they chose to be found by, and their e-mail is their data, not the exporter's.
     */
    public record FriendExportDto(String handle, String displayName, String status, Instant since) {}
}
