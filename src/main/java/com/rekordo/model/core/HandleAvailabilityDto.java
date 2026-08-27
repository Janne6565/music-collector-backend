package com.rekordo.model.core;

/**
 * Whether a handle can be claimed, and if not, why in words the screen can print. The
 * reason is a code rather than a sentence so both clients can translate it.
 */
public record HandleAvailabilityDto(String handle, boolean available, Reason reason) {

    public enum Reason {
        OK,
        /** Too short, too long, or characters that are not letters, numbers or dots. */
        MALFORMED,
        /** Somebody else has it, or gave it up too recently for it to be free again. */
        TAKEN,
        /** A path segment or a word the app needs for itself. */
        RESERVED
    }

    public static HandleAvailabilityDto ok(String handle) {
        return new HandleAvailabilityDto(handle, true, Reason.OK);
    }

    public static HandleAvailabilityDto no(String handle, Reason reason) {
        return new HandleAvailabilityDto(handle, false, reason);
    }
}
