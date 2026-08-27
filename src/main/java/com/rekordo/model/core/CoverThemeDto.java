package com.rekordo.model.core;

/**
 * The palette sampled from a release's cover art, computed once at import.
 *
 * <p>{@code dark} is the decision the clients act on: a sleeve whose dominant tone is
 * darker than the threshold gets the dark chrome, anything lighter gets the light one.
 * Deciding it here means an anonymous client — which has no server to ask — still gets a
 * themed detail screen straight from the metadata proxy, and the theme is right on first
 * paint rather than flashing after the image loads.
 */
public record CoverThemeDto(String dominantColor, String accentColor, double lightness, boolean dark) {}
