package com.musiccollector.model.core;

/** One line of someone else's wishlist. What they are hunting for, and nothing about them. */
public record SharedWishDto(
        String id,
        String albumId,
        String title,
        String artistName,
        Integer year,
        String desiredFormat,
        Long createdAt) {}
