package com.rekordo.model.core;

/** One line of someone else's wishlist. What they are hunting for, and nothing about them. */
public record SharedWishDto(
        String id,
        String albumId,
        /**
         * The pressing they picked, when they picked one.
         *
         * Sent so a friend's list draws the sleeve its owner is looking at rather than a
         * different pressing of the same album. It is a pointer into the catalogue, not
         * anything about them -- their uploaded pictures stay private, as they always have.
         */
        String releaseId,
        String title,
        String artistName,
        Integer year,
        String desiredFormat,
        Long createdAt) {}
