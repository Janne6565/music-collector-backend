package com.rekordo.model.core;

import jakarta.validation.constraints.Min;

/**
 * The square the user framed, in the source picture's own pixels (27b, 27c).
 *
 * <p>The client sends the original bytes and this rectangle rather than a picture it
 * cropped itself: the server renders the sizes, so every device produces the same circle
 * from the same choice, and a client with a different canvas implementation cannot make
 * one collector's picture subtly unlike everybody else's.
 *
 * <p>Clamped rather than validated into a 400. A rectangle that runs off the edge is a
 * rounding difference between the preview and the decoded image, not a person doing
 * something wrong, and refusing the upload over one pixel would be absurd.
 */
public record AvatarCropDto(@Min(0) int x, @Min(0) int y, @Min(1) int size) {}
