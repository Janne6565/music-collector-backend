package com.rekordo.model.exception;

import org.springframework.http.HttpStatus;

/**
 * The account is full: this upload would take it past its storage allowance.
 *
 * <p>Deliberately a different status from {@link PhotoTooLargeException}, which is the same
 * refusal about a different subject. "This picture is too big" is fixed by choosing another
 * picture; "there is no room left" is fixed by deleting one, and a client that cannot tell
 * the two apart can only offer the wrong advice half the time.
 */
public class StorageQuotaExceededException extends BaseException {

    public StorageQuotaExceededException(long usedBytes, long quotaBytes) {
        super(
                HttpStatus.INSUFFICIENT_STORAGE,
                "This account has used %d MB of its %d MB of picture storage. Delete a photo to make room."
                        .formatted(usedBytes / 1_000_000, quotaBytes / 1_000_000));
    }
}
